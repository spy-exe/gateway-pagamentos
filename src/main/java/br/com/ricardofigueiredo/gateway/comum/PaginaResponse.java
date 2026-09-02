package br.com.ricardofigueiredo.gateway.comum;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de paginacao proprio. Evita expor o JSON do Page do Spring Data,
 * que carrega campos internos e muda de formato entre versoes.
 */
public record PaginaResponse<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalDeItens,
        int totalDePaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
