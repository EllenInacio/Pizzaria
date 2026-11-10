package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public static final List<String> STATUS_ORDENADOS = List.of(
            Pedido.RECEBIDO, Pedido.EM_PREPARACAO, Pedido.PRONTO,
            Pedido.SAIU_PARA_ENTREGA, Pedido.RETIRADO, Pedido.FINALIZADO, Pedido.CANCELADO);

    private static final Set<String> STATUS_EM_ANDAMENTO = Set.of(
            Pedido.RECEBIDO, Pedido.EM_PREPARACAO, Pedido.PRONTO, Pedido.SAIU_PARA_ENTREGA, Pedido.RETIRADO);

    private static final int LIMITE_MAXIMO = 50;
    private static final long MAXIMO_DIAS_PREENCHIDOS = 366;
    private static final LocalDateTime INICIO_PADRAO = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    // ---------- Estruturas de resposta da API ----------

    public record Filtro(LocalDate dataInicio, LocalDate dataFim, String status) {
    }

    public record Resumo(long totalPedidos, long pedidosValidos, long pedidosEmAndamento,
            long pedidosFinalizados, long pedidosCancelados, BigDecimal taxaCancelamento,
            BigDecimal faturamento, BigDecimal ticketMedio, BigDecimal totalDescontos, long itensVendidos) {
    }

    public record VendaPeriodo(String periodo, long quantidadePedidos, BigDecimal faturamento) {
    }

    public record SituacaoStatus(String status, long quantidade, BigDecimal percentual) {
    }

    public record ProdutoVendido(int posicao, String produtoId, String nomeProduto,
            long quantidade, BigDecimal faturamento) {
    }

    public record PedidoRecente(String id, String clienteNome, String status, int quantidadeItens,
            BigDecimal valorTotal, LocalDateTime dataHoraEntrada, String registradoPor) {
    }

    // ---------- Filtros (RF05) ----------

    public Filtro criarFiltro(String dataInicio, String dataFim, String status) {
        LocalDate inicio = converterData(dataInicio, "dataInicio");
        LocalDate fim = converterData(dataFim, "dataFim");
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A dataInicio não pode ser posterior à dataFim.");
        }
        String statusNormalizado = null;
        if (status != null && !status.isBlank()) {
            statusNormalizado = status.trim().toUpperCase();
            if (!STATUS_ORDENADOS.contains(statusNormalizado)) {
                throw new IllegalArgumentException("Status inválido. Use: " + String.join(", ", STATUS_ORDENADOS) + ".");
            }
        }
        return new Filtro(inicio, fim, statusNormalizado);
    }

    public int validarLimite(Integer limite, int padrao) {
        int valor = limite == null ? padrao : limite;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private LocalDate converterData(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em " + campo + ". Use o formato AAAA-MM-DD.");
        }
    }

    private List<Pedido> buscarPedidos(Filtro filtro, boolean aplicarStatus) {
        LocalDateTime inicio = filtro.dataInicio() != null ? filtro.dataInicio().atStartOfDay() : INICIO_PADRAO;
        LocalDateTime fim = filtro.dataFim() != null
                ? filtro.dataFim().plusDays(1).atStartOfDay()
                : LocalDate.now().plusDays(1).atStartOfDay();

        List<Pedido> pedidos = pedidoRepository
                .buscarPorPeriodoDeEntrada(inicio, fim);
        if (pedidos == null) {
            return List.of();
        }
        return pedidos.stream()
                .filter(p -> p.getDataHoraEntrada() != null)
                .filter(p -> !aplicarStatus || filtro.status() == null || filtro.status().equals(p.getStatus()))
                .collect(Collectors.toList());
    }

    // ---------- RF01: indicadores gerais ----------

    public Resumo resumo(Filtro filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro, true);

        long total = pedidos.size();
        long cancelados = pedidos.stream().filter(this::cancelado).count();
        long finalizados = pedidos.stream().filter(p -> Pedido.FINALIZADO.equals(p.getStatus())).count();
        long emAndamento = pedidos.stream().filter(p -> p.getStatus() != null && STATUS_EM_ANDAMENTO.contains(p.getStatus())).count();

        List<Pedido> validos = pedidos.stream().filter(p -> !cancelado(p)).toList();
        BigDecimal faturamento = somar(validos.stream().map(Pedido::getValorTotal).toList());
        BigDecimal descontos = somar(validos.stream().map(Pedido::getValorDesconto).toList());
        long itens = validos.stream().mapToLong(this::quantidadeItens).sum();

        BigDecimal ticketMedio = validos.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : faturamento.divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP);

        return new Resumo(total, validos.size(), emAndamento, finalizados, cancelados,
                percentual(cancelados, total), faturamento, ticketMedio, descontos, itens);
    }

    // ---------- RF02: vendas por período ----------

    public List<VendaPeriodo> vendasPorPeriodo(Filtro filtro, String agrupamento) {
        String tipo = agrupamento == null || agrupamento.isBlank() ? "DIA" : agrupamento.trim().toUpperCase();
        if (!tipo.equals("DIA") && !tipo.equals("MES")) {
            throw new IllegalArgumentException("Agrupamento inválido. Use DIA ou MES.");
        }

        List<Pedido> validos = buscarPedidos(filtro, true).stream().filter(p -> !cancelado(p)).toList();

        TreeMap<String, List<Pedido>> grupos = new TreeMap<>();
        for (Pedido p : validos) {
            LocalDate data = p.getDataHoraEntrada().toLocalDate();
            String chave = tipo.equals("DIA") ? data.toString() : YearMonth.from(data).toString();
            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(p);
        }

        List<String> periodos = periodosDoIntervalo(filtro, validos, tipo);
        List<VendaPeriodo> resultado = new ArrayList<>();
        for (String periodo : periodos) {
            List<Pedido> doPeriodo = grupos.getOrDefault(periodo, List.of());
            resultado.add(new VendaPeriodo(periodo, doPeriodo.size(),
                    somar(doPeriodo.stream().map(Pedido::getValorTotal).toList())));
        }
        return resultado;
    }

    private List<String> periodosDoIntervalo(Filtro filtro, List<Pedido> pedidos, String tipo) {
        if (pedidos.isEmpty() && (filtro.dataInicio() == null || filtro.dataFim() == null)) {
            return List.of();
        }
        LocalDate inicio = filtro.dataInicio() != null ? filtro.dataInicio()
                : pedidos.stream().map(p -> p.getDataHoraEntrada().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
        LocalDate fim = filtro.dataFim() != null ? filtro.dataFim()
                : pedidos.stream().map(p -> p.getDataHoraEntrada().toLocalDate()).max(LocalDate::compareTo).orElseThrow();

        List<String> periodos = new ArrayList<>();
        if (tipo.equals("DIA")) {
            if (ChronoUnit.DAYS.between(inicio, fim) > MAXIMO_DIAS_PREENCHIDOS) {
                return pedidos.stream().map(p -> p.getDataHoraEntrada().toLocalDate().toString())
                        .distinct().sorted().toList();
            }
            for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                periodos.add(d.toString());
            }
        } else {
            for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
                periodos.add(m.toString());
            }
        }
        return periodos;
    }

    // ---------- RF03: situação dos pedidos ----------

    public List<SituacaoStatus> situacaoPedidos(Filtro filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro, false);
        Map<String, Long> contagem = pedidos.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Pedido::getStatus, Collectors.counting()));

        List<SituacaoStatus> resultado = new ArrayList<>();
        for (String status : STATUS_ORDENADOS) {
            long quantidade = contagem.getOrDefault(status, 0L);
            resultado.add(new SituacaoStatus(status, quantidade, percentual(quantidade, pedidos.size())));
        }
        return resultado;
    }

    // ---------- RF04: produtos mais vendidos ----------

    public List<ProdutoVendido> produtosMaisVendidos(Filtro filtro, int limite) {
        Map<String, String> nomes = new HashMap<>();
        Map<String, Long> quantidades = new HashMap<>();
        Map<String, BigDecimal> faturamentos = new HashMap<>();

        for (Pedido pedido : buscarPedidos(filtro, true)) {
            if (cancelado(pedido) || pedido.getItens() == null) {
                continue;
            }
            for (ItemPedido item : pedido.getItens()) {
                if (item == null || item.getQuantidade() == null) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                if (chave == null) {
                    continue;
                }
                nomes.putIfAbsent(chave, item.getNomeProduto());
                quantidades.merge(chave, item.getQuantidade().longValue(), Long::sum);
                faturamentos.merge(chave, item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO, BigDecimal::add);
            }
        }

        List<String> ordenados = quantidades.keySet().stream()
                .sorted(Comparator.comparing((String k) -> quantidades.get(k)).reversed()
                        .thenComparing((String k) -> faturamentos.get(k), Comparator.reverseOrder())
                        .thenComparing(k -> Objects.toString(nomes.get(k), "")))
                .limit(limite)
                .toList();

        List<ProdutoVendido> ranking = new ArrayList<>();
        int posicao = 1;
        for (String chave : ordenados) {
            ranking.add(new ProdutoVendido(posicao++, chave, nomes.get(chave), quantidades.get(chave),
                    faturamentos.get(chave).setScale(2, RoundingMode.HALF_UP)));
        }
        return ranking;
    }

    // ---------- RF03: pedidos recentes ----------

    public List<PedidoRecente> pedidosRecentes(Filtro filtro, int limite) {
        List<Pedido> pedidos = buscarPedidos(filtro, true).stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed())
                .limit(limite)
                .toList();

        List<String> ids = pedidos.stream().map(Pedido::getClienteId).filter(Objects::nonNull).distinct().toList();
        Map<String, String> nomesClientes = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (Cliente c : clienteRepository.findAllById(ids)) {
                nomesClientes.put(c.getId(), c.getNome());
            }
        }

        return pedidos.stream().map(p -> new PedidoRecente(
                p.getId(),
                nomesClientes.getOrDefault(p.getClienteId(), "Cliente não encontrado"),
                p.getStatus(),
                (int) quantidadeItens(p),
                p.getValorTotal() != null ? p.getValorTotal() : BigDecimal.ZERO.setScale(2),
                p.getDataHoraEntrada(),
                p.getRegistradoPor())).toList();
    }

    // ---------- Auxiliares ----------

    private boolean cancelado(Pedido p) {
        return Pedido.CANCELADO.equals(p.getStatus());
    }

    private long quantidadeItens(Pedido p) {
        if (p.getItens() == null) {
            return 0;
        }
        return p.getItens().stream()
                .filter(i -> i != null && i.getQuantidade() != null)
                .mapToLong(ItemPedido::getQuantidade).sum();
    }

    private BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream().filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentual(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(parte * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
