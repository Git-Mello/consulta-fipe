package br.com.consultafipe.controller;

import br.com.consultafipe.model.domain.PrecoFipe;
import br.com.consultafipe.model.domain.TipoVeiculo;
import br.com.consultafipe.model.service.FipeService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Páginas renderizadas no servidor: a tela inicial e o fragmento com o card do preço. */
@Controller
public class PaginaController {

    private final FipeService service;

    public PaginaController(FipeService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("tipos", List.of(TipoVeiculo.values()));
        return "index";
    }

    /** Devolve só o fragmento do card, que o JavaScript insere na página. */
    @GetMapping("/consulta/{tipo}/{marcaId}/{modeloId}/{anoId}")
    public String consultar(@PathVariable("tipo") TipoVeiculo tipo,
                            @PathVariable("marcaId") @CodigoNumerico String marcaId,
                            @PathVariable("modeloId") @CodigoNumerico String modeloId,
                            @PathVariable("anoId") @CodigoAno String anoId,
                            Model model) {
        PrecoFipe preco = service.buscarPreco(tipo, marcaId, modeloId, anoId);
        model.addAttribute("preco", preco);
        return "fragments/resultado :: card";
    }
}
