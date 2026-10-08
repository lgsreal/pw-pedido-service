package br.com.fiap.microservices.pedido.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemPedidoDTO(
        @NotNull(message = "ID do produto é obrigatório")
        Long produtoId,
        @Positive(message = "Quantidade deve ser positivo")
        Integer quantidade
) {
}
