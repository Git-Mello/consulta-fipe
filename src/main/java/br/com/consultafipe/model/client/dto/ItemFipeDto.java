package br.com.consultafipe.model.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Item das listas da API da FIPE: marcas, modelos e anos têm o mesmo formato.
 * Ex.: {@code {"code":"21","name":"Fiat"}} ou {@code {"code":"2018-5","name":"2018 Flex"}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemFipeDto(String code, String name) {
}
