package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria (RF05). Os registros são apenas inseridos:
 * não existem operações de alteração ou exclusão expostas.
 */
@Document(collection = "auditoria")
public class Auditoria {

    @Id
    private String id;
    private String usuario;
    private String perfil;
    private LocalDateTime dataHora;
    private String operacao;
    private String entidade;
    private String entidadeId;
    private List<CampoAlterado> alteracoes = new ArrayList<>();
    private String justificativa;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getOperacao() {
        return operacao;
    }

    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public List<CampoAlterado> getAlteracoes() {
        return alteracoes;
    }

    public void setAlteracoes(List<CampoAlterado> alteracoes) {
        this.alteracoes = alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    /**
     * Campo modificado com seus valores anterior e novo.
     */
    public static class CampoAlterado {

        private String campo;
        private String valorAnterior;
        private String valorNovo;

        public CampoAlterado() {
        }

        public CampoAlterado(String campo, String valorAnterior, String valorNovo) {
            this.campo = campo;
            this.valorAnterior = valorAnterior;
            this.valorNovo = valorNovo;
        }

        public String getCampo() {
            return campo;
        }

        public void setCampo(String campo) {
            this.campo = campo;
        }

        public String getValorAnterior() {
            return valorAnterior;
        }

        public void setValorAnterior(String valorAnterior) {
            this.valorAnterior = valorAnterior;
        }

        public String getValorNovo() {
            return valorNovo;
        }

        public void setValorNovo(String valorNovo) {
            this.valorNovo = valorNovo;
        }
    }
}
