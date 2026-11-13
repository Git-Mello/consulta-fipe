/*
 * Consulta FIPE: selects em cascata (tipo → marca → modelo → ano) e inserção do card de preço.
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
        tipo: { selecione: 'Escolha o tipo' },
        marca: {
            plural: 'marcas', selecione: 'Escolha a marca', aguardando: 'Primeiro escolha o tipo',
            vazio: 'A Tabela FIPE não tem marcas para este tipo.',
            semResultado: 'Nenhuma marca com esse nome. Confira a grafia ou apague parte da busca.',
        },
        modelo: {
            plural: 'modelos', selecione: 'Escolha o modelo', aguardando: 'Primeiro escolha a marca',
            vazio: 'A Tabela FIPE não tem modelos para esta marca.',
            semResultado: 'Nenhum modelo com esse nome. Tente só uma parte, como "argo" ou "1.0".',
        },
        ano: {
            plural: 'anos', selecione: 'Escolha o ano', aguardando: 'Primeiro escolha o modelo',
            vazio: 'A Tabela FIPE não tem anos para este modelo.',
            semResultado: 'Nenhum ano com esse texto.',
        },
    };

    const TEXTO_CONSULTANDO = 'Consultando a Tabela FIPE…';

    /** Etapas na ordem da cascata. "itens" guarda a lista completa (o filtro trabalha sobre ela). */
    const etapas = ['tipo', 'marca', 'modelo', 'ano'].map((nome) => ({
        nome,
        campo: document.getElementById(`campo-${nome}`),
        select: document.getElementById(nome),
        filtro: document.getElementById(`${nome}-filtro`),
        status: document.getElementById(`${nome}-status`),
        textos: TEXTOS[nome],
        itens: [],
    }));

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

    function habilitar(etapa, ativo) {
        etapa.select.disabled = !ativo;
        if (etapa.filtro) {
            etapa.filtro.disabled = !ativo;
        }
    }

    function marcarOcupado(etapa, ocupado) {
        if (ocupado) {
            etapa.campo.setAttribute('aria-busy', 'true');
        } else {
            etapa.campo.removeAttribute('aria-busy');
        }
    }

    /** Luzes das etapas: as anteriores à primeira sem escolha estão feitas; ela é a atual; o resto, bloqueado. */
    function atualizarLuzes() {
        const atual = etapas.findIndex((etapa) => !etapa.select.value);
        etapas.forEach((etapa, indice) => {
            if (atual === -1 || indice < atual) {
                etapa.campo.dataset.estado = 'feita';
            } else {
                etapa.campo.dataset.estado = indice === atual ? 'atual' : 'bloqueada';
            }
        });
    }

    /** Volta a etapa ao estado inicial: sem opções, desabilitada e com o filtro limpo. */
    function bloquearEtapa(etapa) {
        etapa.itens = [];
        if (etapa.filtro) {
            etapa.filtro.value = '';
        }
        preencherOpcoes(etapa.select, etapa.textos.aguardando, []);
        habilitar(etapa, false);
        marcarOcupado(etapa, false);
        mostrarStatus(etapa, '');
    }

    function mostrarCarregando(etapa) {
        preencherOpcoes(etapa.select, 'Carregando…', []);
        habilitar(etapa, false);
        marcarOcupado(etapa, true);
        mostrarStatus(etapa, `Buscando ${etapa.textos.plural} na Tabela FIPE…`, 'carregando');
    }

    function mostrarItens(etapa, itens) {
        etapa.itens = itens;
        marcarOcupado(etapa, false);
        if (itens.length === 0) {
            preencherOpcoes(etapa.select, etapa.textos.vazio, []);
            habilitar(etapa, false);
            mostrarStatus(etapa, etapa.textos.vazio);
            return;
        }
        preencherOpcoes(etapa.select, `${etapa.textos.selecione} (${itens.length})`, itens);
        habilitar(etapa, true);
        mostrarStatus(etapa, '');
    }

    /** Reaplica o filtro sobre a lista completa. Devolve false se a opção escolhida deixou de aparecer. */
    function aplicarFiltro(etapa) {
        const termos = normalizar(etapa.filtro.value).split(/\s+/).filter(Boolean);
        const visiveis = etapa.itens.filter((item) => termos.every((termo) => item.busca.includes(termo)));
        const escolhido = etapa.select.value;

        preencherOpcoes(etapa.select, `${etapa.textos.selecione} (${visiveis.length})`, visiveis);
        const continuaVisivel = visiveis.some((item) => item.codigo === escolhido);
        if (continuaVisivel) {
            etapa.select.value = escolhido;
        }

        if (termos.length === 0) {
            mostrarStatus(etapa, '');
        } else if (visiveis.length === 0) {
            mostrarStatus(etapa, etapa.textos.semResultado);
        } else {
            mostrarStatus(etapa, `${visiveis.length} de ${etapa.itens.length} ${etapa.textos.plural}`);
        }
        return !escolhido || continuaVisivel;
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
        const semAnimacao = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        areaResultado.scrollIntoView({ block: 'nearest', behavior: semAnimacao ? 'auto' : 'smooth' });
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
        return Object.fromEntries(etapas.map((etapa) => [etapa.nome, etapa.select.value]));
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
        if (!etapas[indice].select.value) {
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
            marcarOcupado(etapa, false);
            preencherOpcoes(etapa.select, 'A lista não carregou', []);
            mostrarErroNaEtapa(etapa, mensagemDe(erro), () => carregarEtapa(etapa));
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

    function aoFiltrar(indice) {
        const continuaValido = aplicarFiltro(etapas[indice]);
        if (!continuaValido) {
            limparDepoisDe(indice); // a opção escolhida sumiu do filtro: as etapas seguintes perdem a base
        }
        atualizarLuzes();
    }

    function iniciar() {
        formulario.addEventListener('submit', (evento) => evento.preventDefault());
        etapas.forEach((etapa, indice) => {
            etapa.select.addEventListener('change', () => aoEscolher(indice));
            if (etapa.filtro) {
                etapa.filtro.addEventListener('input', () => aoFiltrar(indice));
            }
        });
        // O navegador pode restaurar o tipo ao voltar para a página: recomeça a cascata a partir dele.
        if (etapas[0].select.value) {
            aoEscolher(0);
        } else {
            limparResultado();
            atualizarLuzes();
        }
    }

    iniciar();
})();
