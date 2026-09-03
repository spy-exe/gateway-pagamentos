package br.com.ricardofigueiredo.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o agendador, usado hoje apenas pela fila de entrega dos webhooks. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
