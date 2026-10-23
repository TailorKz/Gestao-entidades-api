package com.tailorkz.gestao_entidades.domain.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITMO_V2 = "AES/GCM/NoPadding";
    private static final String ALGORITMO_LEGADO = "AES"; // ECB/PKCS5 — compatibilidade com dados antigos
    private static final String PREFIXO_V2 = "v2:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    // Chave do novo formato (256 bits, derivada via SHA-256). Null se APP_CRYPTO_KEY não estiver definida.
    private static final byte[] CHAVE_V2 = derivarChaveV2();
    // Chave legada (16 bytes) para ler os dados já gravados no formato antigo.
    private static final byte[] CHAVE_LEGADO = derivarChaveLegado();

    private static byte[] derivarChaveV2() {
        String env = System.getenv("APP_CRYPTO_KEY");
        if (env == null || env.isBlank()) return null;
        try {
            return MessageDigest.getInstance("SHA-256").digest(env.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível derivar a chave de criptografia.", e);
        }
    }

    private static byte[] derivarChaveLegado() {
        String env = System.getenv("APP_CRYPTO_KEY");
        String fonte = (env == null || env.isBlank()) ? "IndaciSecretKey!" : env;
        return Arrays.copyOf(fonte.getBytes(StandardCharsets.UTF_8), 16);
    }

    @Override
    public String convertToDatabaseColumn(String dadosAbertos) {
        if (dadosAbertos == null || dadosAbertos.isEmpty()) return null;
        if (CHAVE_V2 == null) {
            throw new IllegalStateException("APP_CRYPTO_KEY não configurada: não é possível criptografar novos dados.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO_V2);
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CHAVE_V2, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(dadosAbertos.getBytes(StandardCharsets.UTF_8));

            byte[] comIv = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, comIv, 0, iv.length);
            System.arraycopy(cifrado, 0, comIv, iv.length, cifrado.length);
            return PREFIXO_V2 + Base64.getEncoder().encodeToString(comIv);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar dados bancários", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dadosCriptografados) {
        if (dadosCriptografados == null || dadosCriptografados.isEmpty()) return null;
        try {
            if (dadosCriptografados.startsWith(PREFIXO_V2)) {
                return decriptarV2(dadosCriptografados.substring(PREFIXO_V2.length()));
            }
            return decriptarLegado(dadosCriptografados);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar dados bancários", e);
        }
    }

    private String decriptarV2(String base64) throws Exception {
        byte[] comIv = Base64.getDecoder().decode(base64);
        byte[] iv = Arrays.copyOfRange(comIv, 0, GCM_IV_BYTES);
        byte[] cifrado = Arrays.copyOfRange(comIv, GCM_IV_BYTES, comIv.length);
        Cipher cipher = Cipher.getInstance(ALGORITMO_V2);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CHAVE_V2, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    private String decriptarLegado(String base64) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITMO_LEGADO);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CHAVE_LEGADO, "AES"));
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }
}
