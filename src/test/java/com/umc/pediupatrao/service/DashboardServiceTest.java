package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DashboardServiceTest {

    private PedidoRepository pedidoRepository;
    private ClienteRepository clienteRepository;
    private DashboardService dashboardService;
    private final List<Pedido> pedidos = new ArrayList<>();

    private static final LocalDate DIA_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate DIA_3 = LocalDate.of(2026, 11, 3);

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        dashboardService = new DashboardService();
        ReflectionTestUtils.setField(dashboardService, "pedidoRepository", pedidoRepository);
        ReflectionTestUtils.setField(dashboardService, "clienteRepository", clienteRepository);
        pedidos.add(pedido("p1", "c1", Pedido.FINALIZADO, DIA_1.atTime(19, 0), "0.00",
                item("prod1", "Pizza Calabresa", 2, "45.00")));
        pedidos.add(pedido("p2", "c1", Pedido.CANCELADO, DIA_1.atTime(20, 0), "0.00",
                item("prod2", "Refrigerante 2L", 1, "12.00")));
        pedidos.add(pedido("p3", "c2", Pedido.EM_PREPARACAO, DIA_3.atTime(21, 0), "10.00",
                item("prod1", "Pizza Calabresa", 1, "45.00"),
                item("prod2", "Refrigerante 2L", 2, "12.00")));

        when(pedidoRepository.buscarPorPeriodoDeEntrada(
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(pedidos);

        Cliente maria = new Cliente();
        maria.setId("c1");
        maria.setNome("Maria");
        when(clienteRepository.findAllById(any(Iterable.class))).thenReturn(List.of(maria));
    }

    private ItemPedido item(String produtoId, String nome, int quantidade, String preco) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setNomeProduto(nome);
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(new BigDecimal(preco));
        item.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(quantidade)));
        return item;
    }

    private Pedido pedido(String id, String clienteId, String status, LocalDateTime entrada,
            String percentualDesconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteId(clienteId);
        p.setStatus(status);
        p.setDataHoraEntrada(entrada);
        p.setItens(new ArrayList<>(List.of(itens)));
        BigDecimal subtotal = p.getItens().stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = subtotal.multiply(new BigDecimal(percentualDesconto))
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        p.setSubtotal(subtotal);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto));
        return p;
    }

    private DashboardService.Filtro semFiltro() {
        return dashboardService.criarFiltro(null, null, null);
    }

    // ---------- RF01 ----------

    @Test
    void resumoDesconsideraCanceladosNoFaturamento() {
        DashboardService.Resumo r = dashboardService.resumo(semFiltro());

        assertEquals(3, r.totalPedidos());
        assertEquals(2, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(1, r.pedidosFinalizados());
        assertEquals(1, r.pedidosEmAndamento());
        assertEquals(new BigDecimal("152.10"), r.faturamento()); 
        assertEquals(new BigDecimal("76.05"), r.ticketMedio());  
        assertEquals(new BigDecimal("6.90"), r.totalDescontos());
        assertEquals(5, r.itensVendidos());                   
        assertEquals(new BigDecimal("33.33"), r.taxaCancelamento());
    }

    @Test
    void resumoSemPedidosRetornaZeros() {
        pedidos.clear();
        DashboardService.Resumo r = dashboardService.resumo(semFiltro());
        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.00"), r.taxaCancelamento());
    }

    // ---------- RF02 ----------

    @Test
    void vendasPorDiaPreencheDiasSemVenda() {
        List<DashboardService.VendaPeriodo> vendas = dashboardService.vendasPorPeriodo(
                dashboardService.criarFiltro("2026-11-01", "2026-11-03", null), "DIA");

        assertEquals(3, vendas.size());
        assertEquals("2026-11-01", vendas.get(0).periodo());
        assertEquals(1, vendas.get(0).quantidadePedidos());           
        assertEquals(new BigDecimal("90.00"), vendas.get(0).faturamento());
        assertEquals(0, vendas.get(1).quantidadePedidos());        
        assertEquals(new BigDecimal("62.10"), vendas.get(2).faturamento());
    }

    @Test
    void vendasPorMesAgrupaNoMesmoPeriodo() {
        List<DashboardService.VendaPeriodo> vendas = dashboardService.vendasPorPeriodo(semFiltro(), "mes");
        assertEquals(1, vendas.size());
        assertEquals("2026-11", vendas.get(0).periodo());
        assertEquals(new BigDecimal("152.10"), vendas.get(0).faturamento());
    }

    @Test
    void agrupamentoInvalidoEhRejeitado() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.vendasPorPeriodo(semFiltro(), "SEMANA"));
    }

    // ---------- RF03 ----------

    @Test
    void situacaoRetornaTodosOsStatusNaOrdemDoFluxo() {
        List<DashboardService.SituacaoStatus> situacao = dashboardService.situacaoPedidos(semFiltro());

        assertEquals(7, situacao.size());
        assertEquals(Pedido.RECEBIDO, situacao.get(0).status());
        assertEquals(0, situacao.get(0).quantidade());
        assertEquals(1, situacao.get(1).quantidade());                 
        assertEquals(new BigDecimal("33.33"), situacao.get(6).percentual());
    }

    @Test
    void pedidosRecentesVemDoMaisNovoParaOMaisAntigo() {
        List<DashboardService.PedidoRecente> recentes = dashboardService.pedidosRecentes(semFiltro(), 2);
        assertEquals(2, recentes.size());
        assertEquals("p3", recentes.get(0).id());
        assertEquals("Cliente não encontrado", recentes.get(0).clienteNome());
        assertEquals("Maria", recentes.get(1).clienteNome());
    }

    // ---------- RF04 ----------

    @Test
    void rankingDeProdutosIgnoraCancelados() {
        List<DashboardService.ProdutoVendido> ranking = dashboardService.produtosMaisVendidos(semFiltro(), 5);

        assertEquals(2, ranking.size());
        assertEquals("Pizza Calabresa", ranking.get(0).nomeProduto());
        assertEquals(3, ranking.get(0).quantidade());
        assertEquals(new BigDecimal("135.00"), ranking.get(0).faturamento());
        assertEquals(2, ranking.get(1).quantidade());                   
    }

    // ---------- RF05 ----------

    @Test
    void filtroPorStatusRestringeOsIndicadores() {
        DashboardService.Resumo r = dashboardService.resumo(dashboardService.criarFiltro(null, null, "finalizado"));
        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("90.00"), r.faturamento());
    }

    @Test
    void filtrosInvalidosSaoRejeitados() {
        assertThrows(IllegalArgumentException.class, () -> dashboardService.criarFiltro("01/11/2026", null, null));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.criarFiltro("2026-11-05", "2026-11-01", null));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.criarFiltro(null, null, "ENTREGUE"));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.validarLimite(0, 5));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.validarLimite(51, 5));
    }

    // ---------- RF07 ----------

    @Test
    void pedidosAntigosSemValoresSaoTratadosComoZero() {
        Pedido antigo = new Pedido();
        antigo.setId("antigo");
        antigo.setStatus(Pedido.FINALIZADO);
        antigo.setDataHoraEntrada(DIA_1.atTime(10, 0));
        antigo.setItens(null);
        pedidos.add(antigo);

        DashboardService.Resumo r = dashboardService.resumo(semFiltro());
        assertEquals(4, r.totalPedidos());
        assertEquals(new BigDecimal("152.10"), r.faturamento());
        assertEquals(5, r.itensVendidos());
    }
}
