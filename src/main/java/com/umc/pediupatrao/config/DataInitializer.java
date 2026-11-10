package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo) {
        return args -> {

            if (repo.findByUsername("admin").isEmpty()) {

                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN"); // Antes o admin era criado sem perfil

                repo.save(user);

                System.out.println("Usuário admin criado!");
            } else {
                // Corrige o admin já existente que tenha sido criado sem perfil válido
                repo.findByUsername("admin").ifPresent(admin -> {
                    if (admin.getRole() == null || !UsuarioService.PERFIS.contains(admin.getRole())) {
                        admin.setRole("ADMIN");
                        repo.save(admin);
                        System.out.println("Perfil ADMIN atribuído ao usuário admin.");
                    }
                });
            }
        };
    }
}