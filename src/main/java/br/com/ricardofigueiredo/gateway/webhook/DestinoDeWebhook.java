package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Guarda contra requisicao forjada do lado do servidor.
 *
 * Quem cadastra o endpoint escolhe para onde este servico vai bater. Sem
 * limite, bastaria apontar para 127.0.0.1, para a faixa privada da rede ou
 * para o endereco de metadados da nuvem, e usar o codigo de resposta como
 * sonda do que existe atras do firewall. O proprio "falhou com 401" ja diz
 * que ha algo escutando ali.
 *
 * A checagem acontece em dois momentos porque um so nao basta. No cadastro,
 * para dar erro na hora quando alguem digita um IP privado. E de novo na hora
 * de enviar, porque um nome de dominio publico pode passar a resolver para um
 * endereco interno depois, que e o truque conhecido como religacao de DNS.
 */
public final class DestinoDeWebhook {

    private static final Set<String> ESQUEMAS = Set.of("http", "https");

    /** Nomes que apontam para dentro por definicao, sem precisar consultar DNS. */
    private static final Set<String> NOMES_LOCAIS = Set.of(
            "localhost", "metadata.google.internal", "metadata");

    private static final Set<String> SUFIXOS_LOCAIS = Set.of(
            ".localhost", ".local", ".internal", ".home.arpa", ".lan");

    private DestinoDeWebhook() {
    }

    /** Conferencia do cadastro: forma da URL e, se for IP literal, se e publico. */
    public static URI exigirFormaValida(String url) {
        URI destino;
        try {
            destino = new URI(url);
        } catch (URISyntaxException excecao) {
            throw new RegraDeNegocioException("A URL informada nao e valida.");
        }

        if (destino.getScheme() == null
                || !ESQUEMAS.contains(destino.getScheme().toLowerCase(Locale.ROOT))) {
            throw new RegraDeNegocioException("A URL precisa usar http ou https.");
        }
        if (destino.getHost() == null || destino.getHost().isBlank()) {
            throw new RegraDeNegocioException("A URL precisa ter um host.");
        }

        String host = destino.getHost().toLowerCase(Locale.ROOT);

        if (NOMES_LOCAIS.contains(host) || SUFIXOS_LOCAIS.stream().anyMatch(host::endsWith)) {
            throw new RegraDeNegocioException(
                    "Enderecos internos e reservados nao podem receber webhooks.");
        }

        // IP escrito na mao da para conferir sem consultar DNS
        if (pareceEndereco(destino.getHost())) {
            try {
                if (reservado(InetAddress.getByName(destino.getHost()))) {
                    throw new RegraDeNegocioException(
                            "Enderecos internos e reservados nao podem receber webhooks.");
                }
            } catch (UnknownHostException excecao) {
                throw new RegraDeNegocioException("A URL informada nao e valida.");
            }
        }

        return destino;
    }

    /**
     * Conferencia do envio: resolve o nome e recusa se qualquer endereco por
     * tras dele cair em faixa reservada.
     */
    public static void exigirDestinoPublico(String url) {
        URI destino = exigirFormaValida(url);

        InetAddress[] enderecos;
        try {
            enderecos = InetAddress.getAllByName(destino.getHost());
        } catch (UnknownHostException excecao) {
            throw new RegraDeNegocioException("O host " + destino.getHost() + " nao resolve.");
        }

        for (InetAddress endereco : enderecos) {
            if (reservado(endereco)) {
                throw new RegraDeNegocioException(
                        "Enderecos internos e reservados nao podem receber webhooks.");
            }
        }
    }

    static boolean reservado(InetAddress endereco) {
        if (endereco.isLoopbackAddress()          // 127.0.0.0/8 e ::1
                || endereco.isAnyLocalAddress()   // 0.0.0.0
                || endereco.isLinkLocalAddress()  // 169.254.0.0/16, onde mora o metadados da nuvem
                || endereco.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || endereco.isMulticastAddress()) {
            return true;
        }

        byte[] octetos = endereco.getAddress();

        if (octetos.length == 4) {
            int primeiro = octetos[0] & 0xFF;
            int segundo = octetos[1] & 0xFF;

            // 100.64.0.0/10, a faixa de NAT de operadora
            if (primeiro == 100 && segundo >= 64 && segundo <= 127) {
                return true;
            }
            // 192.0.0.0/24, 192.0.2.0/24 e as demais faixas de documentacao
            if (primeiro == 192 && segundo == 0) {
                return true;
            }
            // 198.18.0.0/15, reservada para teste de desempenho de rede
            if (primeiro == 198 && (segundo == 18 || segundo == 19)) {
                return true;
            }
            // 240.0.0.0/4, reservada
            return primeiro >= 240;
        }

        // fc00::/7, os enderecos unicos locais do IPv6
        return (octetos[0] & 0xFE) == 0xFC;
    }

    private static boolean pareceEndereco(String host) {
        String limpo = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;

        return limpo.matches("[0-9.]+") || limpo.contains(":");
    }
}
