package br.com.oficina.billing.infrastructure.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida o header {@code x-signature} ("ts=...,v1=...") das notificações do Mercado Pago:
 * v1 = HMAC-SHA256(secret, "id:{data.id};request-id:{x-request-id};ts:{ts};").
 * Sem secret configurado (modo fake/dev) a validação é desligada.
 */
public class AssinaturaWebhookMercadoPago {
    private final String secret;

    public AssinaturaWebhookMercadoPago(String secret) {
        this.secret = secret;
    }

    public boolean habilitada() {
        return secret != null && !secret.isBlank();
    }

    public boolean valida(String xSignature, String xRequestId, String dataId) {
        if (!habilitada()) {
            return true;
        }
        if (xSignature == null || dataId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("ts")) ts = kv[1];
            if (kv.length == 2 && kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifest = new StringBuilder("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return MessageDigest.isEqual(hmac(manifest.toString()).getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    String hmac(String conteudo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
