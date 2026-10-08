package br.com.fiap.microservices.pedido.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CriarPedidoDTO(
        @NotNull(message = "ID do cliente é obrigatório")
        Long clienteId,
        @NotEmpty(message = "Pedido deve ter pelo menos um item")
        @Valid
        List<ItemPedidoDTO> itens
) {
}
