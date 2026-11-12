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

    const MENSAGEM_SEM_CONEXAO = 'Não foi possível falar com o servidor. Verifique sua conexão e tente novamente.';
    const MENSAGEM_INESPERADA = 'Ocorreu um erro inesperado. Tente novamente.';

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
        tipo: { selecione: 'Selecione o tipo' },
        marca: {
            plural: 'marcas', selecione: 'Selecione a marca', aguardando: 'Escolha o tipo primeiro',
            vazio: 'Nenhuma marca disponível para este tipo.', semResultado: 'Nenhuma marca corresponde ao filtro.',
        },
        modelo: {
            plural: 'modelos', selecione: 'Selecione o modelo', aguardando: 'Escolha a marca primeiro',
            vazio: 'Nenhum modelo disponível para esta marca.', semResultado: 'Nenhum modelo corresponde ao filtro.',
        },
        ano: {
            plural: 'anos', selecione: 'Selecione o ano', aguardando: 'Escolha o modelo primeiro',
            vazio: 'Nenhum ano disponível para este modelo.', semResultado: 'Nenhum ano corresponde ao filtro.',
        },
    };

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
        etapa.status.textContent = texto; // também remove um botão "Tentar novamente" anterior
        etapa.status.dataset.tipo = tipo;
    }

    function mostrarErroNaEtapa(etapa, mensagem, tentarDeNovo) {
        mostrarStatus(etapa, mensagem, 'erro');
        const botao = document.createElement('button');
        botao.type = 'button';
        botao.className = 'botao-texto';
        botao.textContent = 'Tentar novamente';
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
        mostrarStatus(etapa, `Carregando ${etapa.textos.plural}…`, 'carregando');
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

    function mostrarDicaNoResultado(texto) {
        const dica = document.createElement('p');
        dica.className = 'resultado-area__dica';
        dica.textContent = texto;
        areaResultado.replaceChildren(dica);
    }

    function limparResultado() {
        areaResultado.removeAttribute('aria-busy');
        mostrarDicaNoResultado('Escolha o tipo, a marca, o modelo e o ano para ver o preço FIPE.');
    }

    function mostrarResultadoCarregando() {
        areaResultado.setAttribute('aria-busy', 'true');
        mostrarDicaNoResultado('Consultando o preço na Tabela FIPE…');
    }

    /** Insere o fragmento gerado pelo nosso servidor (escapado pelo Thymeleaf com th:text). */
    function inserirFragmento(html) {
        areaResultado.removeAttribute('aria-busy');
        areaResultado.innerHTML = html;
        rolarAteResultado();
    }

    /** Erro sem resposta do servidor (ex.: sem conexão): monta o mesmo visual do fragmento de erro. */
    function mostrarErroNoResultado(mensagem) {
        const caixa = document.createElement('div');
        caixa.className = 'resultado resultado--erro';
        caixa.setAttribute('role', 'alert');
        const titulo = document.createElement('p');
        titulo.className = 'resultado__titulo';
        titulo.textContent = 'Não foi possível consultar';
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
            preencherOpcoes(etapa.select, 'Não foi possível carregar', []);
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
        }
    }

    iniciar();
})();
