package br.com.ricardofigueiredo.gateway.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Assinatura no mesmo desenho que Stripe e Adyen usam, e pelo mesmo motivo.
 *
 * O cabecalho sai como "t=<unix>,v1=<hmac>", e o que e assinado nao e so o
 * corpo, e a concatenacao "<t>.<corpo>". Amarrar o instante dentro do material
 * assinado significa que quem interceptar a requisicao nao consegue reapresenta
 * la mais tarde: para mudar o t precisaria refazer o hmac, e para isso
 * precisaria do segredo.
 *
 * Do lado de quem recebe, a conferencia tem duas partes: refazer o hmac e
 * comparar em tempo constante, e recusar o que for mais velho que a tolerancia.
 */
public final class AssinaturaDeWebhook {

    public static final String CABECALHO = "Aval-Assinatura";

    /** Cinco minutos e a tolerancia usual, folgada o bastante para relogio torto. */
    public static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private static final String ALGORITMO = "HmacSHA256";

    private AssinaturaDeWebhook() {
    }

    public static String gerar(String segredo, String corpo, Instant instante) {
        long carimbo = instante.getEpochSecond();
        return "t=" + carimbo + ",v1=" + hmac(segredo, carimbo + "." + corpo);
    }

    /**
     * Confere um cabecalho recebido. Existe para que o teste prove que a
     * assinatura emitida e verificavel, e serve de referencia para quem for
     * escrever o outro lado.
     */
    public static boolean confere(String segredo, String corpo, String cabecalho, Instant agora) {
        if (cabecalho == null) {
            return false;
        }

        String carimbo = null;
        String assinatura = null;

        for (String parte : cabecalho.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length != 2) {
                continue;
            }
            if ("t".equals(chaveValor[0])) {
                carimbo = chaveValor[1];
            } else if ("v1".equals(chaveValor[0])) {
                assinatura = chaveValor[1];
            }
        }

        if (carimbo == null || assinatura == null) {
            return false;
        }

        long instante;
        try {
            instante = Long.parseLong(carimbo);
        } catch (NumberFormatException excecao) {
            return false;
        }

        if (Math.abs(agora.getEpochSecond() - instante) > TOLERANCIA.toSeconds()) {
            return false;
        }

        byte[] esperado = hmac(segredo, instante + "." + corpo).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, assinatura.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String segredo, String material) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException excecao) {
            throw new IllegalStateException("HmacSHA256 deveria existir em qualquer JVM", excecao);
        }
    }
}
