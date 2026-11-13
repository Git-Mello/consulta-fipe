/*
 * Consulta FIPE: etapas em cascata (tipo → marca → modelo → ano) e inserção do card de preço.
 * Tipo e ano são <select>; marca e modelo, caixas de busca com lista (combobox).
 *
 * Organização:
 *   1. HTTP   – chamadas ao nosso servidor (nunca à API da FIPE) e tradução das falhas.
 *   2. Tela   – manipulação do DOM. Textos sempre via textContent / new Option (escapados).
 *   3. Fluxo  – liga os eventos às chamadas e à tela.
 *
 * O único HTML inserido como HTML é o fragmento renderizado pelo nosso servidor (rota /consulta).
 */
(function () {
    'use strict';

    const formulario = document.getElementById('consulta');
    const areaResultado = document.getElementById('resultado');
    if (!formulario || !areaResultado) {
        return;
    }

    // =====================================================================
    // 1. HTTP
    // =====================================================================

    const MENSAGEM_SEM_CONEXAO = 'Sem resposta do servidor. Confira sua conexão com a internet e tente de novo.';
    const MENSAGEM_INESPERADA = 'Um erro do nosso lado interrompeu a consulta. Tente de novo.';

    /** Falha com mensagem pronta para o usuário. */
    class ErroDeConsulta extends Error {
    }

    const rotas = {
        api: formulario.dataset.apiBase,
        consulta: formulario.dataset.consultaBase,
    };

    function caminho(...partes) {
        return partes.map(encodeURIComponent).join('/');
    }

    const urls = {
        marca: (s) => `${rotas.api}/${caminho(s.tipo)}/marcas`,
        modelo: (s) => `${rotas.api}/${caminho(s.tipo)}/marcas/${caminho(s.marca)}/modelos`,
        ano: (s) => `${rotas.api}/${caminho(s.tipo)}/marcas/${caminho(s.marca)}/modelos/${caminho(s.modelo)}/anos`,
        preco: (s) => `${rotas.consulta}/${caminho(s.tipo, s.marca, s.modelo, s.ano)}`,
    };

    async function requisitar(url, aceita, sinal) {
        try {
            return await fetch(url, { headers: { Accept: aceita }, signal: sinal });
        } catch (erro) {
            if (erro.name === 'AbortError') {
                throw erro;
            }
            throw new ErroDeConsulta(MENSAGEM_SEM_CONEXAO);
        }
    }

    /** Busca uma lista JSON; em erro, usa o "detail" do ProblemDetail devolvido pelo servidor. */
    async function buscarLista(url, sinal) {
        const resposta = await requisitar(url, 'application/json', sinal);
        if (!resposta.ok) {
            throw new ErroDeConsulta(await mensagemDoProblema(resposta));
        }
        const itens = await resposta.json();
        if (!Array.isArray(itens)) {
            throw new ErroDeConsulta(MENSAGEM_INESPERADA);
        }
        return itens;
    }

    /** Busca o fragmento HTML do resultado. Card (200) e erro (4xx/5xx) vêm prontos do servidor. */
    async function buscarFragmento(url, sinal) {
        const resposta = await requisitar(url, 'text/html', sinal);
        const tipoConteudo = resposta.headers.get('Content-Type') || '';
        if (!tipoConteudo.includes('text/html')) {
            throw new ErroDeConsulta(MENSAGEM_INESPERADA);
        }
        return resposta.text();
    }

    async function mensagemDoProblema(resposta) {
        try {
            const problema = await resposta.json();
            if (problema && typeof problema.detail === 'string' && problema.detail) {
                return problema.detail;
            }
        } catch (erro) {
            // Corpo não é um ProblemDetail (ex.: erro de um proxy): usa a mensagem genérica abaixo.
        }
        return `${MENSAGEM_INESPERADA} (código ${resposta.status})`;
    }

    // =====================================================================
    // 2. Tela
    // =====================================================================

    const TEXTOS = {
        tipo: {},
        marca: {
            plural: 'marcas', selecione: 'Digite ou escolha a marca', aguardando: 'Primeiro escolha o tipo',
            vazio: 'A Tabela FIPE não tem marcas para este tipo.',
            semResultado: 'Nenhuma marca com esse nome. Confira a grafia ou apague parte da busca.',
            escolhaDaLista: 'Escolha uma marca da lista para continuar.',
        },
        modelo: {
            plural: 'modelos', selecione: 'Digite ou escolha o modelo', aguardando: 'Primeiro escolha a marca',
            vazio: 'A Tabela FIPE não tem modelos para esta marca.',
            semResultado: 'Nenhum modelo com esse nome. Tente só uma parte, como "argo" ou "1.0".',
            escolhaDaLista: 'Escolha um modelo da lista para continuar.',
        },
        ano: {
            plural: 'anos', selecione: 'Escolha o ano', aguardando: 'Primeiro escolha o modelo',
            vazio: 'A Tabela FIPE não tem anos para este modelo.',
        },
    };

    const TEXTO_CONSULTANDO = 'Consultando a Tabela FIPE…';

    /** Quantas opções a busca desenha de uma vez: centenas de itens a cada tecla deixariam a digitação lenta. */
    const LIMITE_DA_LISTA = 100;

    function normalizar(texto) {
        return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
    }

    /** Converte o JSON do servidor em itens de tela, guardando o texto já normalizado para o filtro. */
    function paraItens(lista) {
        return lista
            .filter((item) => item && item.codigo != null)
            .map((item) => {
                const rotulo = String(item.nome ?? item.descricao ?? item.codigo);
                return { codigo: String(item.codigo), rotulo, busca: normalizar(rotulo) };
            });
    }

    function preencherOpcoes(select, textoInicial, itens) {
        const opcoes = [new Option(textoInicial, '')];
        for (const item of itens) {
            opcoes.push(new Option(item.rotulo, item.codigo));
        }
        select.replaceChildren(...opcoes);
    }

    function mostrarStatus(etapa, texto, tipo = '') {
        etapa.status.textContent = texto; // também remove um botão "Tentar de novo" anterior
        etapa.status.dataset.tipo = tipo;
    }

    function mostrarErroNaEtapa(etapa, mensagem, tentarDeNovo) {
        mostrarStatus(etapa, mensagem, 'erro');
        const botao = document.createElement('button');
        botao.type = 'button';
        botao.className = 'botao-texto';
        botao.textContent = 'Tentar de novo';
        botao.addEventListener('click', tentarDeNovo);
        etapa.status.append(' ', botao);
    }

    function rolagem() {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    }

    // ----- Controles das etapas -----
    // <select> (tipo, ano) e campo de busca (marca, modelo) têm a mesma interface, para o fluxo não
    // precisar saber qual é qual:
    //   valor              código escolhido ('' se nenhum)
    //   mostrar(t, itens)  troca a lista; "t" orienta a pessoa (1ª opção do select, placeholder da busca)
    //   habilitar(ativo)
    //   aoMudar(fn)        avisa quando a escolha muda, inclusive quando é desfeita

    function criarControleDeSelect(select) {
        return {
            get valor() {
                return select.value;
            },
            mostrar(texto, itens) {
                preencherOpcoes(select, itens.length ? `${texto} (${itens.length})` : texto, itens);
            },
            habilitar(ativo) {
                select.disabled = !ativo;
            },
            aoMudar(callback) {
                select.addEventListener('change', callback);
            },
        };
    }

    /**
     * Caixa de busca com lista (padrão "combobox com listbox" do WAI-ARIA APG): a pessoa digita, a lista
     * filtra, e só vale o item escolhido da lista. O foco fica sempre na caixa; o item destacado pelo teclado
     * é indicado com aria-activedescendant.
     */
    function criarCombobox(entrada, { campo, textos, avisar }) {
        const lista = document.getElementById(entrada.getAttribute('aria-controls'));
        let itens = [];
        let visiveis = [];   // os itens que passam na busca
        let escolhido = null;
        let ativo = -1;      // posição, em "visiveis", do item destacado pelo teclado
        let aoMudar = () => {};

        /** Termos da busca. Com a escolha feita e o texto intacto, a lista volta a mostrar tudo. */
        function termos() {
            const texto = escolhido && entrada.value === escolhido.rotulo ? '' : entrada.value;
            return normalizar(texto).split(/\s+/).filter(Boolean);
        }

        function contagem(buscando) {
            if (visiveis.length === 0) {
                return textos.semResultado;
            }
            const resumo = buscando
                ? `${visiveis.length} de ${itens.length} ${textos.plural}`
                : `${itens.length} ${textos.plural}`;
            return visiveis.length > LIMITE_DA_LISTA
                ? `${resumo}. A lista mostra os ${LIMITE_DA_LISTA} primeiros: digite para refinar.`
                : resumo;
        }

        function desenharLista() {
            const opcoes = visiveis.slice(0, LIMITE_DA_LISTA).map((item, indice) => {
                const opcao = document.createElement('li');
                opcao.id = `${entrada.id}-opcao-${indice}`;
                opcao.className = item === escolhido ? 'combobox__opcao combobox__opcao--escolhida' : 'combobox__opcao';
                opcao.setAttribute('role', 'option');
                opcao.setAttribute('aria-selected', 'false');
                opcao.dataset.indice = indice;
                opcao.textContent = item.rotulo;
                return opcao;
            });
            lista.replaceChildren(...opcoes);
            ativo = -1;
        }

        function aberta() {
            return entrada.getAttribute('aria-expanded') === 'true';
        }

        function abrir() {
            if (entrada.disabled || itens.length === 0) {
                return;
            }
            const busca = termos();
            visiveis = itens.filter((item) => busca.every((termo) => item.busca.includes(termo)));
            avisar(contagem(busca.length > 0));
            desenharLista();
            const temOpcoes = visiveis.length > 0;
            lista.hidden = !temOpcoes;
            entrada.setAttribute('aria-expanded', String(temOpcoes));
            destacar(escolhido ? visiveis.indexOf(escolhido) : -1);
        }

        function fechar() {
            destacar(-1);
            lista.hidden = true;
            entrada.setAttribute('aria-expanded', 'false');
        }

        /** Destaca o item da posição indicada (-1 tira o destaque) e o mantém visível na rolagem da lista. */
        function destacar(indice) {
            const opcoes = lista.children;
            if (opcoes[ativo]) {
                opcoes[ativo].setAttribute('aria-selected', 'false');
            }
            ativo = indice >= 0 && indice < opcoes.length ? indice : -1;
            if (ativo === -1) {
                entrada.removeAttribute('aria-activedescendant');
                return;
            }
            const opcao = opcoes[ativo];
            opcao.setAttribute('aria-selected', 'true');
            entrada.setAttribute('aria-activedescendant', opcao.id);
            if (opcao.offsetTop < lista.scrollTop) {
                lista.scrollTop = opcao.offsetTop;
            } else if (opcao.offsetTop + opcao.offsetHeight > lista.scrollTop + lista.clientHeight) {
                lista.scrollTop = opcao.offsetTop + opcao.offsetHeight - lista.clientHeight;
            }
        }

        function mover(passo) {
            if (!aberta()) {
                abrir();
            }
            const quantas = lista.children.length;
            if (!aberta() || quantas === 0) {
                return;
            }
            if (ativo === -1) {
                destacar(passo > 0 ? 0 : quantas - 1);
            } else {
                destacar((ativo + passo + quantas) % quantas);
            }
        }

        function escolher(item) {
            escolhido = item;
            entrada.value = item.rotulo;
            fechar();
            avisar('');
            aoMudar();
        }

        function desfazerEscolha() {
            if (escolhido) {
                escolhido = null;
                aoMudar();
            }
        }

        /** No celular, sobe o campo para o topo: a lista abre embaixo dele, acima do teclado virtual. */
        function trazerParaCima() {
            if (window.matchMedia('(max-width: 39.99rem)').matches) {
                campo.scrollIntoView({ block: 'start', behavior: rolagem() });
            }
        }

        function aoTeclar(evento) {
            if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
                evento.preventDefault();
                mover(evento.key === 'ArrowDown' ? 1 : -1);
            } else if (evento.key === 'Enter' && aberta()) {
                // Sem destaque, um resultado único também vale: "argo 1.0" + Enter
                const indice = ativo !== -1 ? ativo : (visiveis.length === 1 ? 0 : -1);
                if (indice !== -1) {
                    evento.preventDefault();
                    escolher(visiveis[indice]);
                }
            } else if (evento.key === 'Escape') {
                if (aberta()) {
                    evento.preventDefault();
                    fechar();
                    if (escolhido) {
                        avisar(''); // a contagem era da lista, que fechou; a escolha continua valendo
                    }
                } else if (entrada.value) {
                    evento.preventDefault();
                    entrada.value = '';
                    avisar('');
                    desfazerEscolha();
                }
            }
        }

        entrada.addEventListener('keydown', aoTeclar);
        entrada.addEventListener('input', () => {
            desfazerEscolha(); // mudou o texto: a escolha anterior não vale mais
            abrir();
        });
        entrada.addEventListener('focus', () => {
            abrir();
            trazerParaCima();
        });
        entrada.addEventListener('click', () => {
            if (!aberta()) {
                abrir();
            }
        });
        entrada.addEventListener('blur', () => {
            fechar();
            if (escolhido || !entrada.value.trim()) {
                avisar('');
            } else {
                avisar(visiveis.length ? textos.escolhaDaLista : textos.semResultado);
            }
        });
        // Clicar ou tocar na lista não tira o foco da caixa; a rolagem por toque não escolhe (só o "click").
        lista.addEventListener('mousedown', (evento) => evento.preventDefault());
        lista.addEventListener('click', (evento) => {
            const opcao = evento.target.closest('[role="option"]');
            if (opcao) {
                escolher(visiveis[Number(opcao.dataset.indice)]);
            }
        });

        return {
            get valor() {
                return escolhido ? escolhido.codigo : '';
            },
            mostrar(texto, novosItens) {
                itens = novosItens;
                visiveis = [];
                escolhido = null;
                entrada.value = '';
                entrada.placeholder = texto;
                fechar();
                lista.replaceChildren();
            },
            habilitar(ativa) {
                entrada.disabled = !ativa;
                if (!ativa) {
                    fechar();
                }
            },
            aoMudar(callback) {
                aoMudar = callback;
            },
        };
    }

    /** Etapas na ordem da cascata, cada uma com o seu controle. */
    const etapas = ['tipo', 'marca', 'modelo', 'ano'].map((nome) => {
        const etapa = {
            nome,
            campo: document.getElementById(`campo-${nome}`),
            status: document.getElementById(`${nome}-status`),
            textos: TEXTOS[nome],
        };
        const elemento = document.getElementById(nome);
        etapa.controle = elemento.tagName === 'SELECT'
            ? criarControleDeSelect(elemento)
            : criarCombobox(elemento, {
                campo: etapa.campo,
                textos: etapa.textos,
                avisar: (texto) => mostrarStatus(etapa, texto),
            });
        return etapa;
    });

    function marcarOcupado(etapa, ocupado) {
        if (ocupado) {
            etapa.campo.setAttribute('aria-busy', 'true');
        } else {
            etapa.campo.removeAttribute('aria-busy');
        }
    }

    /** Luzes das etapas: as anteriores à primeira sem escolha estão feitas; ela é a atual; o resto, bloqueado. */
    function atualizarLuzes() {
        const atual = etapas.findIndex((etapa) => !etapa.controle.valor);
        etapas.forEach((etapa, indice) => {
            if (atual === -1 || indice < atual) {
                etapa.campo.dataset.estado = 'feita';
            } else {
                etapa.campo.dataset.estado = indice === atual ? 'atual' : 'bloqueada';
            }
        });
    }

    /** Volta a etapa ao estado inicial: sem itens, sem escolha e desabilitada. */
    function bloquearEtapa(etapa) {
        etapa.controle.mostrar(etapa.textos.aguardando, []);
        etapa.controle.habilitar(false);
        marcarOcupado(etapa, false);
        mostrarStatus(etapa, '');
    }

    function mostrarCarregando(etapa) {
        etapa.controle.mostrar('Carregando…', []);
        etapa.controle.habilitar(false);
        marcarOcupado(etapa, true);
        mostrarStatus(etapa, `Buscando ${etapa.textos.plural} na Tabela FIPE…`, 'carregando');
    }

    function mostrarItens(etapa, itens) {
        marcarOcupado(etapa, false);
        if (itens.length === 0) {
            etapa.controle.mostrar(etapa.textos.vazio, []);
            etapa.controle.habilitar(false);
            mostrarStatus(etapa, etapa.textos.vazio);
            return;
        }
        etapa.controle.mostrar(etapa.textos.selecione, itens);
        etapa.controle.habilitar(true);
        mostrarStatus(etapa, '');
    }

    function mostrarFalhaNaLista(etapa, mensagem, tentarDeNovo) {
        marcarOcupado(etapa, false);
        etapa.controle.mostrar('A lista não carregou', []);
        mostrarErroNaEtapa(etapa, mensagem, tentarDeNovo);
    }

    // ----- Leitor do preço: hodômetro de tambores -----

    const DIGITOS = '0123456789';
    const VOLTA_INTEIRA = DIGITOS.length;

    /** Tambor com a fita 0–9 duas vezes: ao revelar, dá uma volta inteira antes de parar no dígito. */
    function criarTambor(digito, ordem, centavos) {
        const tambor = document.createElement('span');
        tambor.className = centavos ? 'tambor tambor--centavos' : 'tambor';
        const fita = document.createElement('span');
        fita.className = 'tambor__fita';
        fita.dataset.digito = digito;
        fita.style.setProperty('--ordem', ordem);
        for (const numero of DIGITOS + DIGITOS) {
            const linha = document.createElement('span');
            linha.textContent = numero;
            fita.append(linha);
        }
        tambor.append(fita);
        return tambor;
    }

    function criarTexto(classe, texto) {
        const elemento = document.createElement('span');
        elemento.className = classe;
        elemento.textContent = texto;
        return elemento;
    }

    /**
     * Troca o texto do preço ("R$ 48.136,00") por tambores. O texto continua no elemento, oculto na tela,
     * para leitores de tela lerem o valor de uma vez. Devolve o visor, ou null se o formato for inesperado.
     */
    function montarTambores(preco) {
        const texto = preco.textContent.trim();
        const partes = /^R\$\s*([\d.]+),(\d{2})$/.exec(texto);
        if (!partes) {
            return null; // fica o texto puro, que já é legível
        }
        const visor = document.createElement('span');
        visor.className = 'tambores';
        visor.setAttribute('aria-hidden', 'true');
        visor.append(criarTexto('tambores__moeda', 'R$'));

        let ordem = 0;
        for (const caractere of partes[1]) {
            visor.append(caractere === '.'
                ? criarTexto('tambores__separador', '.')
                : criarTambor(Number(caractere), ordem++, false));
        }
        visor.append(criarTexto('tambores__separador', ','));
        for (const caractere of partes[2]) {
            visor.append(criarTambor(Number(caractere), ordem++, true));
        }

        preco.replaceChildren(visor, criarTexto('visualmente-oculto', texto));
        return visor;
    }

    /** O momento do preço: cada tambor gira uma volta e para no dígito, da esquerda para a direita. */
    function girarTambores(visor) {
        // Dois quadros: o primeiro pinta os tambores no zero; o segundo dispara a transição do CSS.
        requestAnimationFrame(() => requestAnimationFrame(() => {
            for (const fita of visor.querySelectorAll('.tambor__fita')) {
                fita.style.setProperty('--posicao', VOLTA_INTEIRA + Number(fita.dataset.digito));
            }
        }));
    }

    /** Modelo do leitor parado, tirado do HTML da página (com os tambores já montados). */
    const leitorParado = (function () {
        const modelo = areaResultado.querySelector('.resultado--parado');
        if (!modelo) {
            return null;
        }
        const copia = modelo.cloneNode(true);
        const preco = copia.querySelector('.resultado__preco');
        if (preco) {
            montarTambores(preco);
        }
        return copia;
    })();

    function mostrarLeitorParado(textoReferencia) {
        if (!leitorParado) {
            return;
        }
        const leitor = leitorParado.cloneNode(true);
        const referencia = leitor.querySelector('.resultado__referencia');
        if (textoReferencia && referencia) {
            referencia.textContent = textoReferencia;
        }
        areaResultado.replaceChildren(leitor);
    }

    function limparResultado() {
        areaResultado.removeAttribute('aria-busy');
        mostrarLeitorParado('');
    }

    function mostrarResultadoCarregando() {
        areaResultado.setAttribute('aria-busy', 'true');
        mostrarLeitorParado(TEXTO_CONSULTANDO);
    }

    /** Insere o fragmento gerado pelo nosso servidor (escapado pelo Thymeleaf com th:text). */
    function inserirFragmento(html) {
        areaResultado.removeAttribute('aria-busy');
        areaResultado.innerHTML = html;
        const preco = areaResultado.querySelector('.resultado__preco');
        const visor = preco ? montarTambores(preco) : null;
        if (visor) {
            girarTambores(visor);
        }
        rolarAteResultado();
    }

    /** Erro sem resposta do servidor (ex.: sem conexão): monta o mesmo visual do fragmento de erro. */
    function mostrarErroNoResultado(mensagem) {
        const caixa = document.createElement('div');
        caixa.className = 'resultado resultado--erro';
        caixa.setAttribute('role', 'alert');
        caixa.dataset.gravidade = 'temporaria';
        const titulo = document.createElement('p');
        titulo.className = 'resultado__titulo';
        titulo.textContent = 'A consulta não terminou';
        const texto = document.createElement('p');
        texto.className = 'resultado__mensagem';
        texto.textContent = mensagem;
        caixa.append(titulo, texto);
        areaResultado.removeAttribute('aria-busy');
        areaResultado.replaceChildren(caixa);
    }

    function rolarAteResultado() {
        areaResultado.scrollIntoView({ block: 'nearest', behavior: rolagem() });
    }

    // =====================================================================
    // 3. Fluxo
    // =====================================================================

    /** Uma requisição pendente por destino (etapa ou resultado); a nova cancela a anterior. */
    const pendentes = new Map();

    function novaRequisicao(destino) {
        cancelar(destino);
        const controle = new AbortController();
        pendentes.set(destino, controle);
        return controle;
    }

    function cancelar(destino) {
        const controle = pendentes.get(destino);
        if (controle) {
            controle.abort();
            pendentes.delete(destino);
        }
    }

    function selecaoAtual() {
        return Object.fromEntries(etapas.map((etapa) => [etapa.nome, etapa.controle.valor]));
    }

    function mensagemDe(erro) {
        if (erro instanceof ErroDeConsulta) {
            return erro.message;
        }
        console.error('Falha inesperada na consulta FIPE', erro);
        return MENSAGEM_INESPERADA;
    }

    /** Limpa e desabilita todas as etapas depois da indicada, e o resultado. */
    function limparDepoisDe(indice) {
        for (const etapa of etapas.slice(indice + 1)) {
            cancelar(etapa.nome);
            bloquearEtapa(etapa);
        }
        cancelar('resultado');
        limparResultado();
    }

    function aoEscolher(indice) {
        limparDepoisDe(indice);
        atualizarLuzes();
        if (!etapas[indice].controle.valor) {
            return;
        }
        const proxima = etapas[indice + 1];
        if (proxima) {
            carregarEtapa(proxima);
        } else {
            consultarPreco();
        }
    }

    async function carregarEtapa(etapa) {
        const controle = novaRequisicao(etapa.nome);
        mostrarCarregando(etapa);
        try {
            const lista = await buscarLista(urls[etapa.nome](selecaoAtual()), controle.signal);
            mostrarItens(etapa, paraItens(lista));
        } catch (erro) {
            if (erro.name === 'AbortError') {
                return; // a seleção mudou: a resposta antiga é descartada
            }
            mostrarFalhaNaLista(etapa, mensagemDe(erro), () => carregarEtapa(etapa));
        } finally {
            if (pendentes.get(etapa.nome) === controle) {
                pendentes.delete(etapa.nome);
            }
        }
    }

    async function consultarPreco() {
        const controle = novaRequisicao('resultado');
        mostrarResultadoCarregando();
        try {
            inserirFragmento(await buscarFragmento(urls.preco(selecaoAtual()), controle.signal));
        } catch (erro) {
            if (erro.name === 'AbortError') {
                return;
            }
            mostrarErroNoResultado(mensagemDe(erro));
        } finally {
            if (pendentes.get('resultado') === controle) {
                pendentes.delete('resultado');
            }
        }
    }

    function iniciar() {
        formulario.addEventListener('submit', (evento) => evento.preventDefault());
        etapas.forEach((etapa, indice) => etapa.controle.aoMudar(() => aoEscolher(indice)));
        // O navegador pode restaurar o tipo ao voltar para a página: recomeça a cascata a partir dele.
        if (etapas[0].controle.valor) {
            aoEscolher(0);
        } else {
            limparResultado();
            atualizarLuzes();
        }
    }

    iniciar();
})();
