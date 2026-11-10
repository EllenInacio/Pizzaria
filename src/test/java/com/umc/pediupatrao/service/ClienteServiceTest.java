package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class ClienteServiceTest {

    private ClienteRepository clienteRepository;
    private AuditoriaRepository auditoriaRepository;
    private ClienteService clienteService;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        auditoriaRepository = mock(AuditoriaRepository.class);

        AuditoriaService auditoriaService = new AuditoriaService();
        ReflectionTestUtils.setField(auditoriaService, "auditoriaRepository", auditoriaRepository);

        clienteService = new ClienteService();
        ReflectionTestUtils.setField(clienteService, "clienteRepository", clienteRepository);
        ReflectionTestUtils.setField(clienteService, "auditoriaService", auditoriaService);

        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditoriaRepository.save(any(Auditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Cliente clienteExistente() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        cliente.setTelefone("(11) 99999-0000");
        cliente.setLogradouro("Rua das Flores");
        cliente.setEndereco("Endereço antigo"); // campo legado
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
        return cliente;
    }

    @Test
    void alteracaoDeEnderecoRegistraValorAnteriorENovo() {
        clienteExistente();
        Cliente dados = new Cliente();
        dados.setId("c1");
        dados.setNome("Maria");
        dados.setTelefone("(11) 99999-0000");
        dados.setLogradouro("Av. Brasil");

        Cliente atualizado = clienteService.salvar(dados);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        Auditoria auditoria = captor.getValue();
        assertEquals(AuditoriaService.ALTERACAO_CLIENTE, auditoria.getOperacao());
        assertEquals("atendente01", auditoria.getUsuario());
        assertEquals("ATENDENTE", auditoria.getPerfil());
        assertEquals("c1", auditoria.getEntidadeId());
        assertEquals(1, auditoria.getAlteracoes().size());
        assertEquals("logradouro", auditoria.getAlteracoes().get(0).getCampo());
        assertEquals("Rua das Flores", auditoria.getAlteracoes().get(0).getValorAnterior());
        assertEquals("Av. Brasil", auditoria.getAlteracoes().get(0).getValorNovo());
        assertEquals("Endereço antigo", atualizado.getEndereco()); // campo legado preservado
    }

    @Test
    void salvarSemAlteracoesNaoGeraAuditoria() {
        clienteExistente();
        Cliente dados = new Cliente();
        dados.setId("c1");
        dados.setNome("Maria");
        dados.setTelefone("(11) 99999-0000");
        dados.setLogradouro("Rua das Flores");
        dados.setNumero(""); // vazio equivale a não informado

        clienteService.salvar(dados);

        verify(auditoriaRepository, never()).save(any(Auditoria.class));
    }
}
