package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    /** Desconto máximo permitido, em percentual (RF04). */
    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");

    /** Transições válidas do fluxo obrigatório (RF03). CANCELADO tem operação própria. */
    private static final Map<String, Set<String>> TRANSICOES = Map.of(
            Pedido.RECEBIDO, Set.of(Pedido.EM_PREPARACAO),
            Pedido.EM_PREPARACAO, Set.of(Pedido.PRONTO),
            Pedido.PRONTO, Set.of(Pedido.SAIU_PARA_ENTREGA, Pedido.RETIRADO),
            Pedido.SAIU_PARA_ENTREGA, Set.of(Pedido.FINALIZADO),
            Pedido.RETIRADO, Set.of(Pedido.FINALIZADO));

    /** Status anteriores à saída: únicos em que cabem desconto e cancelamento. */
    private static final Set<String> ANTES_DA_SAIDA = Set.of(
            Pedido.RECEBIDO, Pedido.EM_PREPARACAO, Pedido.PRONTO);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * Registra um novo pedido. Da requisição são aproveitados somente o cliente,
     * os produtos e as quantidades; preços, total, status, responsável e
     * data/hora de entrada são definidos pelo servidor.
     */
    public Pedido criarPedido(Pedido dados) {
        if (dados == null || dados.getClienteId() == null || dados.getClienteId().isBlank()) {
            throw new IllegalArgumentException("Informe o cliente do pedido.");
        }
        if (!clienteRepository.existsById(dados.getClienteId())) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (dados.getItens() == null || dados.getItens().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um item no pedido.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(dados.getClienteId());

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedido itemInformado : dados.getItens()) {
            if (itemInformado == null || itemInformado.getProdutoId() == null
                    || itemInformado.getProdutoId().isBlank()) {
                throw new IllegalArgumentException("Informe o produto de cada item.");
            }
            Integer quantidade = itemInformado.getQuantidade();
            if (quantidade == null || quantidade < 1) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser de no mínimo 1.");
            }
            Produto produto = produtoRepository.findById(itemInformado.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() < 0) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " não possui preço válido.");
            }

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
            itens.add(item);
        }
        pedido.setItens(itens);
        pedido.setPercentualDesconto(BigDecimal.ZERO.setScale(2));
        recalcularValores(pedido);

        pedido.setStatus(Pedido.RECEBIDO);
        pedido.setRegistradoPor(AuditoriaService.usuarioAtual());
        pedido.setDataHoraEntrada(LocalDateTime.now());

        Pedido salvo = pedidoRepository.save(pedido);

        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "status", null, salvo.getStatus());
        AuditoriaService.adicionarSeAlterado(alteracoes, "clienteId", null, salvo.getClienteId());
        AuditoriaService.adicionarSeAlterado(alteracoes, "itens", null, descreverItens(salvo));
        AuditoriaService.adicionarSeAlterado(alteracoes, "valorTotal", null, salvo.getValorTotal());
        AuditoriaService.adicionarSeAlterado(alteracoes, "dataHoraEntrada", null, salvo.getDataHoraEntrada());
        auditoriaService.registrar(AuditoriaService.INCLUSAO_PEDIDO, "Pedido", salvo.getId(), alteracoes, null);

        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido não encontrado."));
    }

    /**
     * Avança o pedido no fluxo obrigatório, impedindo transições inválidas.
     * Na saída (SAIU_PARA_ENTREGA / RETIRADO) registra data/hora e responsável.
     */
    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscarPorId(id);
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Informe o novo status.");
        }
        String novoStatus = status.trim().toUpperCase();
        String statusAtual = pedido.getStatus();

        if (Pedido.CANCELADO.equals(statusAtual)) {
            throw new IllegalStateException("Pedido cancelado não pode ter o status alterado.");
        }
        if (Pedido.CANCELADO.equals(novoStatus)) {
            throw new IllegalStateException(
                    "O cancelamento deve ser feito pela operação de cancelamento, exclusiva do GERENTE, com justificativa.");
        }
        Set<String> permitidos = statusAtual == null ? Set.of() : TRANSICOES.getOrDefault(statusAtual, Set.of());
        if (!permitidos.contains(novoStatus)) {
            throw new IllegalStateException("Transição inválida: " + statusAtual + " → " + novoStatus + ".");
        }

        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "status", statusAtual, novoStatus);
        pedido.setStatus(novoStatus);

        if (Pedido.SAIU_PARA_ENTREGA.equals(novoStatus) || Pedido.RETIRADO.equals(novoStatus)) {
            LocalDateTime agora = LocalDateTime.now();
            String responsavel = AuditoriaService.usuarioAtual();
            AuditoriaService.adicionarSeAlterado(alteracoes, "dataHoraSaida", pedido.getDataHoraSaida(), agora);
            AuditoriaService.adicionarSeAlterado(alteracoes, "responsavelSaida", pedido.getResponsavelSaida(), responsavel);
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(responsavel);
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(AuditoriaService.ALTERACAO_STATUS_PEDIDO, "Pedido", salvo.getId(), alteracoes, null);
        return salvo;
    }

    /**
     * Aplica desconto percentual (até 20%) antes da saída do pedido.
     * Operação exclusiva do GERENTE.
     */
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        verificarGerente();
        Pedido pedido = buscarPorId(id);

        if (Pedido.CANCELADO.equals(pedido.getStatus())) {
            throw new IllegalStateException("Não é possível aplicar desconto em pedido cancelado.");
        }
        if (!antesDaSaida(pedido.getStatus())) {
            throw new IllegalStateException(
                    "O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0
                || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("O desconto deve ser maior que 0% e de no máximo 20%.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalStateException("Pedido sem itens registrados: não é possível calcular o desconto.");
        }

        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal valorDescontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        recalcularValores(pedido);
        pedido.setDescontoAplicadoPor(AuditoriaService.usuarioAtual());
        pedido.setDataHoraDesconto(LocalDateTime.now());

        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "percentualDesconto", percentualAnterior, pedido.getPercentualDesconto());
        AuditoriaService.adicionarSeAlterado(alteracoes, "valorDesconto", valorDescontoAnterior, pedido.getValorDesconto());
        AuditoriaService.adicionarSeAlterado(alteracoes, "valorTotal", totalAnterior, pedido.getValorTotal());

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(AuditoriaService.DESCONTO_PEDIDO, "Pedido", salvo.getId(), alteracoes, null);
        return salvo;
    }

    /**
     * Cancela o pedido com justificativa, preservando seus dados.
     * Operação exclusiva do GERENTE e permitida apenas antes da saída.
     */
    public Pedido cancelarPedido(String id, String justificativa) {
        verificarGerente();
        Pedido pedido = buscarPorId(id);

        if (Pedido.CANCELADO.equals(pedido.getStatus())) {
            throw new IllegalStateException("O pedido já está cancelado.");
        }
        if (!antesDaSaida(pedido.getStatus())) {
            throw new IllegalStateException(
                    "Não é possível cancelar um pedido que já saiu para entrega, foi retirado ou finalizado.");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória.");
        }
        String motivo = justificativa.trim();
        if (motivo.length() > 500) {
            throw new IllegalArgumentException("A justificativa deve ter no máximo 500 caracteres.");
        }

        String statusAnterior = pedido.getStatus();
        pedido.setStatus(Pedido.CANCELADO);
        pedido.setJustificativaCancelamento(motivo);
        pedido.setCanceladoPor(AuditoriaService.usuarioAtual());
        pedido.setDataHoraCancelamento(LocalDateTime.now());

        List<Auditoria.CampoAlterado> alteracoes = new ArrayList<>();
        AuditoriaService.adicionarSeAlterado(alteracoes, "status", statusAnterior, Pedido.CANCELADO);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(AuditoriaService.CANCELAMENTO_PEDIDO, "Pedido", salvo.getId(), alteracoes, motivo);
        return salvo;
    }

    /** Recalcula subtotal, desconto e total a partir dos itens (cálculo no servidor). */
    private void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            BigDecimal subtotalItem = item.getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setSubtotal(subtotalItem);
            subtotal = subtotal.add(subtotalItem);
        }
        BigDecimal percentual = pedido.getPercentualDesconto() == null
                ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal desconto = subtotal.multiply(percentual)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    private boolean antesDaSaida(String status) {
        return status != null && ANTES_DA_SAIDA.contains(status);
    }

    /** Regra reforçada na camada de serviço, além da configuração do Spring Security. */
    private void verificarGerente() {
        if (!"GERENTE".equals(AuditoriaService.perfilAtual())) {
            throw new AccessDeniedException("Operação exclusiva do perfil GERENTE.");
        }
    }

    private String descreverItens(Pedido pedido) {
        return pedido.getItens().stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " (R$ " + i.getPrecoUnitario() + ")")
                .collect(Collectors.joining("; "));
    }
}
