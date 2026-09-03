package br.com.ricardofigueiredo.gateway.config;

import br.com.ricardofigueiredo.gateway.seguranca.FiltroAutenticacaoJwt;
import br.com.ricardofigueiredo.gateway.seguranca.RespostaNaoAutorizado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/api/v1/autenticacao/registro",
            "/api/v1/autenticacao/login",
            "/api/v1/webhooks/eco",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private final FiltroAutenticacaoJwt filtroAutenticacaoJwt;
    private final RespostaNaoAutorizado respostaNaoAutorizado;

    public SegurancaConfig(FiltroAutenticacaoJwt filtroAutenticacaoJwt,
                           RespostaNaoAutorizado respostaNaoAutorizado) {
        this.filtroAutenticacaoJwt = filtroAutenticacaoJwt;
        this.respostaNaoAutorizado = respostaNaoAutorizado;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/saude").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros.authenticationEntryPoint(respostaNaoAutorizado))
                .addFilterBefore(filtroAutenticacaoJwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager gerenciadorDeAutenticacao(AuthenticationConfiguration configuracao)
            throws Exception {
        return configuracao.getAuthenticationManager();
    }
}
