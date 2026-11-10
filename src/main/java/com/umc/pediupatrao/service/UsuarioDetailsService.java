package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    // Atribui a role conforme o perfil salvo (ADMIN, GERENTE ou ATENDENTE).
                    // Perfis desconhecidos/antigos não recebem nenhuma permissão.
                    String perfil = usuario.getRole() == null ? "" : usuario.getRole().trim().toUpperCase();
                    List<GrantedAuthority> authorities = UsuarioService.PERFIS.contains(perfil)
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + perfil))
                            : List.of();
                    return User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword())
                            .authorities(authorities)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
