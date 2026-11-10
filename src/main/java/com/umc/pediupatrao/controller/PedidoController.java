package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public Pedido criarPedido(@RequestBody Pedido pedido) {
        return pedidoService.criarPedido(pedido);
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    // Exclusivo do GERENTE (SecurityConfig + PedidoService)
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody Map<String, String> dados) {
        String valor = dados.get("percentual");
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Informe o percentual de desconto.");
        }
        BigDecimal percentual = new BigDecimal(valor.trim().replace(",", "."));
        return pedidoService.aplicarDesconto(id, percentual);
    }

    // Exclusivo do GERENTE (SecurityConfig + PedidoService)
    @PostMapping("/{id}/cancelar")
    public Pedido cancelarPedido(@PathVariable String id, @RequestBody Map<String, String> dados) {
        return pedidoService.cancelarPedido(id, dados.get("justificativa"));
    }
}
