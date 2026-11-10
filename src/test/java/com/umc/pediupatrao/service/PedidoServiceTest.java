package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class PedidoServiceTest {

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private ClienteRepository clienteRepository;
    private AuditoriaRepository auditoriaRepository;
    private PedidoService pedidoService;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        auditoriaRepository = mock(AuditoriaRepository.class);

        AuditoriaService auditoriaService = new AuditoriaService();
        ReflectionTestUtils.setField(auditoriaService, "auditoriaRepository", auditoriaRepository);

        pedidoService = new PedidoService();
        ReflectionTestUtils.setField(pedidoService, "pedidoRepository", pedidoRepository);
        ReflectionTestUtils.setField(pedidoService, "produtoRepository", produtoRepository);
        ReflectionTestUtils.setField(pedidoService, "clienteRepository", clienteRepository);
        ReflectionTestUtils.setField(pedidoService, "auditoriaService", auditoriaService);

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditoriaRepository.save(any(Auditoria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void logar(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    /** Pedido com 2x R$ 40,00 = R$ 80,00 no status informado. */
    private Pedido pedidoNoStatus(String status) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("prod1");
        item.setNomeProduto("Pizza Calabresa");
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("40.00"));
        item.setSubtotal(new BigDecimal("80.00"));

        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setClienteId("c1");
        pedido.setItens(new ArrayList<>(List.of(item)));
        pedido.setSubtotal(new BigDecimal("80.00"));
        pedido.setPercentualDesconto(new BigDecimal("0.00"));
        pedido.setValorDesconto(new BigDecimal("0.00"));
        pedido.setValorTotal(new BigDecimal("80.00"));
        pedido.setStatus(status);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private Auditoria ultimaAuditoria() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<Auditoria> registros = captor.getAllValues();
        return registros.get(registros.size() - 1);
    }

    // ---------- RF02: registro e cálculo no servidor ----------

    @Test
    void criarPedidoCalculaTotalNoServidorIgnorandoValoresEnviados() {
        logar("atendente01", "ATENDENTE");
        Produto produto = new Produto();
        produto.setId("prod1");
        produto.setNome("Pizza Calabresa");
        produto.setPreco(40.0);
        produto.setAtivo(true);
        when(clienteRepository.existsById("c1")).thenReturn(true);
        when(produtoRepository.findById("prod1")).thenReturn(Optional.of(produto));

        ItemPedido item = new ItemPedido();
        item.setProdutoId("prod1");
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("0.01")); // tentativa de manipular o preço
        Pedido dados = new Pedido();
        dados.setClienteId("c1");
        dados.setItens(List.of(item));
        dados.setValorTotal(new BigDecimal("1.00"));   // tentativa de manipular o total
        dados.setStatus(Pedido.FINALIZADO);            // tentativa de pular o fluxo

        Pedido criado = pedidoService.criarPedido(dados);

        assertEquals(new BigDecimal("80.00"), criado.getValorTotal());
        assertEquals(new BigDecimal("40.00"), criado.getItens().get(0).getPrecoUnitario());
        assertEquals(Pedido.RECEBIDO, criado.getStatus());
        assertEquals("atendente01", criado.getRegistradoPor());
        assertNotNull(criado.getDataHoraEntrada());

        Auditoria auditoria = ultimaAuditoria();
        assertEquals(AuditoriaService.INCLUSAO_PEDIDO, auditoria.getOperacao());
        assertEquals("atendente01", auditoria.getUsuario());
        assertEquals("ATENDENTE", auditoria.getPerfil());
    }

    @Test
    void criarPedidoSemItensEhRejeitado() {
        logar("atendente01", "ATENDENTE");
        when(clienteRepository.existsById("c1")).thenReturn(true);
        Pedido dados = new Pedido();
        dados.setClienteId("c1");
        assertThrows(IllegalArgumentException.class, () -> pedidoService.criarPedido(dados));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    // ---------- RF03: fluxo e transições ----------

    @Test
    void fluxoCompletoRegistraSaidaComResponsavel() {
        logar("atendente01", "ATENDENTE");
        Pedido pedido = pedidoNoStatus(Pedido.RECEBIDO);

        pedidoService.atualizarStatus("p1", Pedido.EM_PREPARACAO);
        pedidoService.atualizarStatus("p1", Pedido.PRONTO);
        pedidoService.atualizarStatus("p1", Pedido.SAIU_PARA_ENTREGA);

        assertNotNull(pedido.getDataHoraSaida());
        assertEquals("atendente01", pedido.getResponsavelSaida());

        pedidoService.atualizarStatus("p1", Pedido.FINALIZADO);
        assertEquals(Pedido.FINALIZADO, pedido.getStatus());
        assertEquals(AuditoriaService.ALTERACAO_STATUS_PEDIDO, ultimaAuditoria().getOperacao());
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        logar("atendente01", "ATENDENTE");
        pedidoNoStatus(Pedido.EM_PREPARACAO);
        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("p1", Pedido.FINALIZADO));
    }

    @Test
    void naoPermiteCancelarPelaAlteracaoDeStatus() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.RECEBIDO);
        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("p1", Pedido.CANCELADO));
    }

    @Test
    void naoPermiteAlterarStatusAposCancelamento() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.CANCELADO);
        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("p1", Pedido.EM_PREPARACAO));
    }

    // ---------- RF04: desconto ----------

    @Test
    void gerenteAplicaDescontoDeAte20PorCento() {
        logar("gerente01", "GERENTE");
        Pedido pedido = pedidoNoStatus(Pedido.PRONTO);

        pedidoService.aplicarDesconto("p1", new BigDecimal("10"));

        assertEquals(new BigDecimal("8.00"), pedido.getValorDesconto());
        assertEquals(new BigDecimal("72.00"), pedido.getValorTotal());
        Auditoria auditoria = ultimaAuditoria();
        assertEquals(AuditoriaService.DESCONTO_PEDIDO, auditoria.getOperacao());
        assertTrue(auditoria.getAlteracoes().stream().anyMatch(c -> c.getCampo().equals("valorTotal")
                && "80.00".equals(c.getValorAnterior()) && "72.00".equals(c.getValorNovo())));
    }

    @Test
    void descontoAcimaDe20PorCentoEhRejeitado() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.RECEBIDO);
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("20.01")));
    }

    @Test
    void descontoPorAdminOuAtendenteEhNegado() {
        pedidoNoStatus(Pedido.RECEBIDO);
        logar("admin", "ADMIN");
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("5")));
        logar("atendente01", "ATENDENTE");
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("5")));
    }

    @Test
    void descontoAposSaidaEhRejeitado() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.SAIU_PARA_ENTREGA);
        assertThrows(IllegalStateException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("5")));
    }

    // ---------- RF04: cancelamento ----------

    @Test
    void gerenteCancelaComJustificativaPreservandoDados() {
        logar("gerente01", "GERENTE");
        Pedido pedido = pedidoNoStatus(Pedido.EM_PREPARACAO);

        pedidoService.cancelarPedido("p1", "Cliente desistiu");

        assertEquals(Pedido.CANCELADO, pedido.getStatus());
        assertEquals("Cliente desistiu", pedido.getJustificativaCancelamento());
        assertEquals("gerente01", pedido.getCanceladoPor());
        assertEquals(1, pedido.getItens().size()); // histórico preservado
        Auditoria auditoria = ultimaAuditoria();
        assertEquals(AuditoriaService.CANCELAMENTO_PEDIDO, auditoria.getOperacao());
        assertEquals("Cliente desistiu", auditoria.getJustificativa());
    }

    @Test
    void cancelamentoSemJustificativaEhRejeitado() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.RECEBIDO);
        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelarPedido("p1", "  "));
    }

    @Test
    void cancelamentoPorAdminEhNegado() {
        logar("admin", "ADMIN");
        pedidoNoStatus(Pedido.RECEBIDO);
        assertThrows(AccessDeniedException.class, () -> pedidoService.cancelarPedido("p1", "Teste"));
    }

    @Test
    void cancelamentoAposRetiradaEhRejeitado() {
        logar("gerente01", "GERENTE");
        pedidoNoStatus(Pedido.RETIRADO);
        assertThrows(IllegalStateException.class, () -> pedidoService.cancelarPedido("p1", "Teste"));
    }
}
