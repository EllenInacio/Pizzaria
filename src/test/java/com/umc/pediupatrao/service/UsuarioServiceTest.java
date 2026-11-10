package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UsuarioServiceTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioService = new UsuarioService(usuarioRepository, new BCryptPasswordEncoder());
        when(usuarioRepository.findByUsername(any(String.class))).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword("hash");
        usuario.setRole(role);
        return usuario;
    }

    @Test
    void perfilInvalidoEhRejeitado() {
        Usuario novo = usuario(null, "joao", "SUPERUSER");
        novo.setPassword("123456");
        assertThrows(IllegalArgumentException.class, () -> usuarioService.salvarUsuario(novo));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void criacaoIgnoraIdInformadoECriptografaSenha() {
        Usuario novo = usuario("id-de-outro-usuario", "gerente01", "gerente");
        novo.setPassword("123456");

        usuarioService.salvarUsuario(novo);

        assertNull(novo.getId());
        assertEquals("GERENTE", novo.getRole());
        assertEquals(true, new BCryptPasswordEncoder().matches("123456", novo.getPassword()));
    }

    @Test
    void adminNaoAlteraOProprioPerfil() {
        Usuario admin = usuario("u1", "admin", "ADMIN");
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        Usuario dados = usuario(null, "admin", "ATENDENTE");
        assertThrows(IllegalArgumentException.class, () -> usuarioService.atualizarUsuario("u1", dados));
    }

    @Test
    void adminNaoExcluiOProprioUsuario() {
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));
        assertThrows(IllegalArgumentException.class, () -> usuarioService.deletarUsuario("u1"));
        verify(usuarioRepository, never()).deleteById(any(String.class));
    }

    @Test
    void adminAtribuiPerfilAOutroUsuario() {
        Usuario atendente = usuario("u2", "atendente01", "ATENDENTE");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(atendente));

        Usuario dados = usuario(null, "atendente01", "GERENTE");
        dados.setPassword(""); // senha não informada no formulário

        usuarioService.atualizarUsuario("u2", dados);

        assertEquals("GERENTE", atendente.getRole());
        assertEquals("hash", atendente.getPassword()); // senha em branco não é alterada
    }
}
