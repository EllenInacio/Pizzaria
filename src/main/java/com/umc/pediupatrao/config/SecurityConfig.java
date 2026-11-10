package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF habilitado (padrão do Spring Security): os formulários Thymeleaf
                // recebem o token automaticamente e as chamadas fetch enviam o cabeçalho X-CSRF-TOKEN
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole("ADMIN")

                        // Auditoria: somente ADMIN e GERENTE (apenas consulta)
                        .requestMatchers(HttpMethod.GET, "/auditoria").hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/auditoria/**").denyAll()

                        // Pedidos
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/api/pedidos").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").denyAll()

                        // Clientes: ATENDENTE cadastra/atualiza; GERENTE apenas consulta
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes").hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole("ATENDENTE")

                        // Produtos: leitura para montar pedidos; gestão por ADMIN e GERENTE
                        .requestMatchers(HttpMethod.GET, "/api/produtos").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // APIs sem login respondem 401 (em vez de redirecionar para a tela de login)
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    /**
     * Registra a tentativa de acesso indevido (sem dados sensíveis) e responde 403:
     * JSON para as APIs e a página de erro para as telas.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String usuario = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anônimo";
            log.warn("Acesso negado: usuário={} método={} recurso={}", usuario, request.getMethod(), request.getRequestURI());

            if (request.getRequestURI().startsWith("/api/")) {
                String mensagem = accessDeniedException instanceof CsrfException
                        ? "Token CSRF ausente ou inválido. Recarregue a página e tente novamente."
                        : "Acesso negado: operação não permitida para o seu perfil.";
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"erro\": \"" + mensagem + "\"}");
            } else {
                response.sendError(HttpStatus.FORBIDDEN.value(), "Acesso negado");
            }
        };
    }
}
