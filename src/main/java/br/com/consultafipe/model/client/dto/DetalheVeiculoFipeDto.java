package br.com.consultafipe.model.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resposta de {@code GET /{vehicleType}/brands/{brandId}/models/{modelId}/years/{yearId}}.
 * Ex.: {@code {"vehicleType":1,"price":"R$ 48.136,00","brand":"Fiat","model":"ARGO DRIVE 1.0 6V Flex",
 * "modelYear":2018,"fuel":"Flex","codeFipe":"001494-0","referenceMonth":"novembro de 2026","fuelAcronym":"F"}}.
 * O preço já vem formatado em reais. Só os campos usados pela aplicação estão aqui; os demais
 * ({@code vehicleType}, {@code fuelAcronym}, {@code priceHistory}...) são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetalheVeiculoFipeDto(
        String price,
        String brand,
        String model,
        Integer modelYear,
        String fuel,
        String codeFipe,
        String referenceMonth) {
}
