package br.com.consultafipe.controller;

import br.com.consultafipe.model.domain.AnoModelo;
import br.com.consultafipe.model.domain.Marca;
import br.com.consultafipe.model.domain.Modelo;
import br.com.consultafipe.model.domain.TipoVeiculo;
import br.com.consultafipe.model.service.FipeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listas em JSON que alimentam os selects em cascata da tela. */
@RestController
@RequestMapping("/api/{tipo}/marcas")
public class CatalogoController {

    private final FipeService service;

    public CatalogoController(FipeService service) {
        this.service = service;
    }

    @GetMapping
    public List<Marca> marcas(@PathVariable("tipo") TipoVeiculo tipo) {
        return service.listarMarcas(tipo);
    }

    @GetMapping("/{marcaId}/modelos")
    public List<Modelo> modelos(@PathVariable("tipo") TipoVeiculo tipo,
                                @PathVariable("marcaId") @CodigoNumerico String marcaId) {
        return service.listarModelos(tipo, marcaId);
    }

    @GetMapping("/{marcaId}/modelos/{modeloId}/anos")
    public List<AnoModelo> anos(@PathVariable("tipo") TipoVeiculo tipo,
                                @PathVariable("marcaId") @CodigoNumerico String marcaId,
                                @PathVariable("modeloId") @CodigoNumerico String modeloId) {
        return service.listarAnos(tipo, marcaId, modeloId);
    }
}
