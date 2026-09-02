package br.com.ricardofigueiredo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * O relogio e injetado em vez de chamado direto para que os testes possam
 * fixar uma data e verificar regras que dependem de vencimento de cartao.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
