package br.com.fiap.microservices.pedido.service;

import br.com.fiap.microservices.pedido.dto.ClienteDTO;
import br.com.fiap.microservices.pedido.dto.CriarPedidoDTO;
import br.com.fiap.microservices.pedido.dto.ItemPedidoDTO;
import br.com.fiap.microservices.pedido.dto.ProdutoDTO;
import br.com.fiap.microservices.pedido.feign.CatalogoFeignClient;
import br.com.fiap.microservices.pedido.feign.ClienteFeignClient;
import br.com.fiap.microservices.pedido.model.ItemPedido;
import br.com.fiap.microservices.pedido.model.Pedido;
import br.com.fiap.microservices.pedido.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {
    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);
    private final PedidoRepository pedidoRepository;
    private final CatalogoFeignClient catalogoFeignClient;
    private final ClienteFeignClient clienteFeignClient;

    public PedidoService(PedidoRepository pedidoRepository,
                         CatalogoFeignClient catalogoFeignClient,
                         ClienteFeignClient clienteFeignClient) {
        this.pedidoRepository = pedidoRepository;
        this.catalogoFeignClient = catalogoFeignClient;
        this.clienteFeignClient = clienteFeignClient;
    }

    @Transactional
    public Pedido criar(CriarPedidoDTO dto) {
        logger.info("Criando novo pedido para cliente ID: {}", dto.clienteId());

        // 1. Validar se o cliente exite (ClienteFeignClient)
        try {
            ClienteDTO cliente = clienteFeignClient.buscarClientePorId(dto.clienteId());
            logger.info("Cliente encontrado: {}", cliente.nome());
        } catch (Exception e) {
            logger.error("Erro ao buscar cliente ID: {}", dto.clienteId(), e);
            throw new RuntimeException("Cliente não encontrado com ID: "+ dto.clienteId());
        }

        // 2. Processar os itens do pedido (CatalogoFeignClient)
        BigDecimal valorTotal = BigDecimal.ZERO;
        List<ItemPedido> itensProcessados = new ArrayList<>();

        for (ItemPedidoDTO item : dto.itens()) {
            logger.info("Processando item - Produto ID: {}, Quantidade: {}", item.produtoId(), item.quantidade());
            try {
                ProdutoDTO produto = catalogoFeignClient.buscarProdutoPorId(item.produtoId());
                logger.info("Produto encontrado: {} - Estoque: {}", produto.nome(), produto.estoque());
                // 3. Validar estoque
                if (produto.estoque() < item.quantidade()) {
                    logger.error("Estoque insuficiente para produto: {}", produto.nome());
                    throw new RuntimeException("Estoque insuficiente para produto: "+ produto.nome()
                            + ". Disponível: "+ produto.estoque()
                            + ". Solicitado: "+ item.quantidade());
                }
                // 4. Criar item do pedido com preço do catálogo
                ItemPedido itemProcessado = new ItemPedido();
                itemProcessado.setProdutoId(item.produtoId());
                itemProcessado.setQuantidade(item.quantidade());
                itemProcessado.setPreco(produto.preco());
                itensProcessados.add(itemProcessado);
                // 5. Acumular o valor total
                BigDecimal subtotal = itemProcessado.calcularSubtotal();
                valorTotal = valorTotal.add(subtotal);
                logger.info("Subtotal do item: {}", subtotal);
            } catch (Exception e) {
                logger.error("Erro ao buscar produto ID: {}", item.produtoId(), e);
                throw new RuntimeException("Produto não encontrado com ID: "+ item.produtoId());
            }
        }

        // 6. Criar e persistir o pedido
        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.clienteId());
        pedido.setStatus("PENDENTE");
        pedido.setValorTotal(valorTotal);
        for (ItemPedido item : itensProcessados) {
            pedido.adicionarItem(item);
        }
        pedido.setDataPedido(LocalDateTime.now());

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        logger.info("Pedido criado com ID: {} - Valor total: {}", pedidoSalvo.getId(), pedidoSalvo.getValorTotal());

        // 7. Retornar o Pedido populado com os dados do cliente e dos produtos
        return pedidoSalvo;
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new RuntimeException("Pedido não encontrado com ID: "+ id));
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    public List<Pedido> listarPorCliente(Long clienteId) {
        return pedidoRepository.findByClienteId(clienteId);
    }
}
