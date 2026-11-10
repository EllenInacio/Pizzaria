package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    // Operações auditadas
    public static final String INCLUSAO_PEDIDO = "INCLUSÃO DE PEDIDO";
    public static final String ALTERACAO_STATUS_PEDIDO = "ALTERAÇÃO DE STATUS DO PEDIDO";
    public static final String DESCONTO_PEDIDO = "DESCONTO NO PEDIDO";
    public static final String CANCELAMENTO_PEDIDO = "CANCELAMENTO DE PEDIDO";
    public static final String INCLUSAO_CLIENTE = "INCLUSÃO DE CLIENTE";
    public static final String ALTERACAO_CLIENTE = "ALTERAÇÃO DE CLIENTE";
    public static final String EXCLUSAO_CLIENTE = "EXCLUSÃO DE CLIENTE";

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    /**
     * Grava um novo registro de auditoria com o usuário autenticado, seu perfil
     * e a data/hora atual. Nunca recebe senhas ou credenciais.
     */
    public Auditoria registrar(String operacao, String entidade, String entidadeId,
            List<Auditoria.CampoAlterado> alteracoes, String justificativa) {
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(usuarioAtual());
        auditoria.setPerfil(perfilAtual());
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        if (alteracoes != null) {
            auditoria.setAlteracoes(alteracoes);
        }
        auditoria.setJustificativa(justificativa);
        return auditoriaRepository.save(auditoria);
    }

    public List<Auditoria> listarTodos() {
        return auditoriaRepository.findAllByOrderByDataHoraDesc();
    }

    /**
     * Adiciona o campo à lista somente se o valor mudou
     * (null e texto vazio são considerados equivalentes).
     */
    public static void adicionarSeAlterado(List<Auditoria.CampoAlterado> alteracoes,
            String campo, Object valorAnterior, Object valorNovo) {
        String anterior = normalizar(valorAnterior);
        String novo = normalizar(valorNovo);
        if (!Objects.equals(anterior, novo)) {
            alteracoes.add(new Auditoria.CampoAlterado(campo, anterior, novo));
        }
    }

    private static String normalizar(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }

    /** Username do usuário autenticado. */
    public static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return "sistema";
        }
        return auth.getName();
    }

    /** Perfil (ADMIN, GERENTE ou ATENDENTE) do usuário autenticado. */
    public static String perfilAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "SEM_PERFIL";
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String nome = authority.getAuthority();
            if (nome != null && nome.startsWith("ROLE_")
                    && UsuarioService.PERFIS.contains(nome.substring(5))) {
                return nome.substring(5);
            }
        }
        return "SEM_PERFIL";
    }
}
