package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pediupatrao.dados-demo", havingValue = "true")
public class DadosDemoInitializer {

    private static final String RESPONSAVEL = "carga-demo";

    @Bean
    CommandLineRunner carregarDadosDemo(PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository, ClienteRepository clienteRepository) {
        return args -> {
            if (pedidoRepository.existsByRegistradoPor(RESPONSAVEL)) {
                System.out.println("Dados de demonstração já carregados.");
                return;
            }

            List<Produto> produtos = produtoRepository.findAll().stream()
                    .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null)
                    .toList();
            if (produtos.isEmpty()) {
                produtos = List.of(
                        produto(produtoRepository, "Pizza Calabresa", "Pizza", 45.0),
                        produto(produtoRepository, "Pizza Margherita", "Pizza", 42.0),
                        produto(produtoRepository, "Pizza Portuguesa", "Pizza", 48.0),
                        produto(produtoRepository, "Pizza Quatro Queijos", "Pizza", 52.0),
                        produto(produtoRepository, "Refrigerante 2L", "Bebida", 12.0),
                        produto(produtoRepository, "Porção de Batata", "Porção", 28.0));
            }

            List<Cliente> clientes = clienteRepository.findAll();
            if (clientes.isEmpty()) {
                clientes = List.of(
                        cliente(clienteRepository, "Cliente Demonstração 1", "(11) 90000-0001"),
                        cliente(clienteRepository, "Cliente Demonstração 2", "(11) 90000-0002"),
                        cliente(clienteRepository, "Cliente Demonstração 3", "(11) 90000-0003"));
            }

            Random random = new Random(42); 
            LocalDate hoje = LocalDate.now();
            int total = 0;
            for (int dias = 29; dias >= 0; dias--) {
                LocalDate dia = hoje.minusDays(dias);
                int pedidosNoDia = 2 + random.nextInt(4);
                for (int i = 0; i < pedidosNoDia; i++) {
                    Pedido pedido = new Pedido();
                    pedido.setClienteId(clientes.get(random.nextInt(clientes.size())).getId());
                    pedido.setRegistradoPor(RESPONSAVEL);
                    pedido.setDataHoraEntrada(dia.atTime(18 + random.nextInt(5), random.nextInt(60)));

                    List<ItemPedido> itens = new ArrayList<>();
                    int quantidadeItens = 1 + random.nextInt(3);
                    for (int j = 0; j < quantidadeItens; j++) {
                        Produto produto = produtos.get(random.nextInt(produtos.size()));
                        ItemPedido item = new ItemPedido();
                        item.setProdutoId(produto.getId());
                        item.setNomeProduto(produto.getNome());
                        item.setQuantidade(1 + random.nextInt(2));
                        item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
                        item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
                        itens.add(item);
                    }
                    pedido.setItens(itens);

                    BigDecimal subtotal = itens.stream().map(ItemPedido::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal percentual = random.nextInt(10) == 0 ? new BigDecimal("10.00") : new BigDecimal("0.00");
                    BigDecimal desconto = subtotal.multiply(percentual).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    pedido.setSubtotal(subtotal);
                    pedido.setPercentualDesconto(percentual);
                    pedido.setValorDesconto(desconto);
                    pedido.setValorTotal(subtotal.subtract(desconto));

                    // Pedidos antigos finalizados (alguns cancelados); os de hoje ficam em andamento
                    if (random.nextInt(12) == 0) {
                        pedido.setStatus(Pedido.CANCELADO);
                        pedido.setJustificativaCancelamento("Carga de demonstração");
                        pedido.setCanceladoPor(RESPONSAVEL);
                        pedido.setDataHoraCancelamento(pedido.getDataHoraEntrada().plusMinutes(10));
                    } else if (dias == 0) {
                        String[] andamento = {Pedido.RECEBIDO, Pedido.EM_PREPARACAO, Pedido.PRONTO, Pedido.SAIU_PARA_ENTREGA};
                        pedido.setStatus(andamento[random.nextInt(andamento.length)]);
                    } else {
                        pedido.setStatus(Pedido.FINALIZADO);
                    }
                    if (Pedido.FINALIZADO.equals(pedido.getStatus()) || Pedido.SAIU_PARA_ENTREGA.equals(pedido.getStatus())) {
                        pedido.setDataHoraSaida(pedido.getDataHoraEntrada().plusMinutes(35));
                        pedido.setResponsavelSaida(RESPONSAVEL);
                    }

                    pedidoRepository.save(pedido);
                    total++;
                }
            }
            System.out.println("Dados de demonstração carregados: " + total + " pedidos.");
        };
    }

    private Produto produto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return repo.save(p);
    }

    private Cliente cliente(ClienteRepository repo, String nome, String telefone) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setTelefone(telefone);
        return repo.save(c);
    }
}
