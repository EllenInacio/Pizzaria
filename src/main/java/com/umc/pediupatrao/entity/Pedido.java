package com.umc.pediupatrao.entity;

import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
@Entity
public class Pedido {

    // Status do fluxo obrigatório do pedido (RF03)
    public static final String RECEBIDO = "RECEBIDO";
    public static final String EM_PREPARACAO = "EM_PREPARACAO";
    public static final String PRONTO = "PRONTO";
    public static final String SAIU_PARA_ENTREGA = "SAIU_PARA_ENTREGA";
    public static final String RETIRADO = "RETIRADO";
    public static final String FINALIZADO = "FINALIZADO";
    public static final String CANCELADO = "CANCELADO";

    @Id
    private String id;
    private String clienteId;

    // Campos originais (mantidos para compatibilidade com pedidos antigos)
    private String tipoPizza;
    private String status;
    private int quantidade;
    private String sabor;

    // Itens, preços e valores (RF02) — calculados no servidor
    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal percentualDesconto;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;
    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;

    // Entrada do pedido (RF02/RF03)
    private String registradoPor;
    private LocalDateTime dataHoraEntrada;

    // Saída do pedido: SAIU_PARA_ENTREGA ou RETIRADO (RF03)
    private String responsavelSaida;
    private LocalDateTime dataHoraSaida;

    // Cancelamento (RF04)
    private String justificativaCancelamento;
    private String canceladoPor;
    private LocalDateTime dataHoraCancelamento;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getTipoPizza() {
        return tipoPizza;
    }

    public void setTipoPizza(String tipoPizza) {
        this.tipoPizza = tipoPizza;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public String getSabor() {
        return sabor;
    }

    public void setSabor(String sabor) {
        this.sabor = sabor;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getDescontoAplicadoPor() {
        return descontoAplicadoPor;
    }

    public void setDescontoAplicadoPor(String descontoAplicadoPor) {
        this.descontoAplicadoPor = descontoAplicadoPor;
    }

    public LocalDateTime getDataHoraDesconto() {
        return dataHoraDesconto;
    }

    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) {
        this.dataHoraDesconto = dataHoraDesconto;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(String registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getResponsavelSaida() {
        return responsavelSaida;
    }

    public void setResponsavelSaida(String responsavelSaida) {
        this.responsavelSaida = responsavelSaida;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public void setDataHoraSaida(LocalDateTime dataHoraSaida) {
        this.dataHoraSaida = dataHoraSaida;
    }

    public String getJustificativaCancelamento() {
        return justificativaCancelamento;
    }

    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(String canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public LocalDateTime getDataHoraCancelamento() {
        return dataHoraCancelamento;
    }

    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) {
        this.dataHoraCancelamento = dataHoraCancelamento;
    }
}
