package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.DashboardService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    // RF01 — Indicadores gerais de vendas
    @GetMapping("/resumo")
    public DashboardService.Resumo resumo(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status) {
        return dashboardService.resumo(dashboardService.criarFiltro(dataInicio, dataFim, status));
    }

    // RF02 — Vendas por período (DIA ou MES)
    @GetMapping("/vendas-por-periodo")
    public List<DashboardService.VendaPeriodo> vendasPorPeriodo(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendasPorPeriodo(
                dashboardService.criarFiltro(dataInicio, dataFim, status), agrupamento);
    }

    // RF03 — Situação dos pedidos por status
    @GetMapping("/situacao-pedidos")
    public List<DashboardService.SituacaoStatus> situacaoPedidos(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status) {
        return dashboardService.situacaoPedidos(dashboardService.criarFiltro(dataInicio, dataFim, status));
    }

    // RF04 — Ranking dos produtos mais vendidos
    @GetMapping("/produtos-mais-vendidos")
    public List<DashboardService.ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(
                dashboardService.criarFiltro(dataInicio, dataFim, status),
                dashboardService.validarLimite(limite, 5));
    }

    // RF03 — Últimos pedidos registrados
    @GetMapping("/pedidos-recentes")
    public List<DashboardService.PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(
                dashboardService.criarFiltro(dataInicio, dataFim, status),
                dashboardService.validarLimite(limite, 10));
    }
}
