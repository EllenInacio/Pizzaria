package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class UsuarioService {

    /** Perfis de acesso válidos (RF01). */
    public static final Set<String> PERFIS = Set.of("ADMIN", "GERENTE", "ATENDENTE");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Salva um novo usuário (criptografando a senha)
    public void salvarUsuario(Usuario usuario) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("Informe o username.");
        }
        usuario.setUsername(usuario.getUsername().trim());
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com este username.");
        }
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("Informe a senha.");
        }
        usuario.setRole(validarPerfil(usuario.getRole()));
        usuario.setId(null); // Impede sobrescrever outro usuário informando um ID
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword())); // Criptografa a senha
        usuarioRepository.save(usuario);
    }

    // Deleta um usuário pelo ID
    public void deletarUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (usuario.getUsername().equals(AuditoriaService.usuarioAtual())) {
            throw new IllegalArgumentException("Não é permitido excluir o próprio usuário.");
        }
        usuarioRepository.deleteById(id);
    }

    // Lista todos os usuários
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Atualiza o usuário usuários
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (usuarioAtualizado.getUsername() == null || usuarioAtualizado.getUsername().isBlank()) {
            throw new IllegalArgumentException("Informe o username.");
        }
        String novoUsername = usuarioAtualizado.getUsername().trim();
        Optional<Usuario> mesmoUsername = usuarioRepository.findByUsername(novoUsername);
        if (mesmoUsername.isPresent() && !mesmoUsername.get().getId().equals(id)) {
            throw new IllegalArgumentException("Já existe um usuário com este username.");
        }

        String novoPerfil = validarPerfil(usuarioAtualizado.getRole());
        // Proteção contra autoelevação/alteração do próprio perfil
        boolean proprioUsuario = usuarioExistente.getUsername().equals(AuditoriaService.usuarioAtual());
        if (proprioUsuario && !novoPerfil.equals(usuarioExistente.getRole())) {
            throw new IllegalArgumentException("Não é permitido alterar o próprio perfil de acesso.");
        }
        if (proprioUsuario && !novoUsername.equals(usuarioExistente.getUsername())) {
            throw new IllegalArgumentException("Não é permitido alterar o próprio username.");
        }

        usuarioExistente.setUsername(novoUsername);
        usuarioExistente.setRole(novoPerfil);

        // Só atualiza a senha se uma nova foi informada
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        usuarioRepository.save(usuarioExistente);
    }

    /** Aceita somente ADMIN, GERENTE ou ATENDENTE. */
    private String validarPerfil(String perfil) {
        String normalizado = perfil == null ? "" : perfil.trim().toUpperCase();
        if (!PERFIS.contains(normalizado)) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return normalizado;
    }
}
