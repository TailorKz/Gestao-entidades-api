package com.tailorkz.gestao_entidades.domain.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";

    private static final byte[] KEY = montarChave();

    private static byte[] montarChave() {
        String env = System.getenv("APP_CRYPTO_KEY");
        String fonte = (env == null || env.isEmpty()) ? "IndaciSecretKey!" : env;
        byte[] bytes = fonte.getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(bytes, 16);
    }

    @Override
    public String convertToDatabaseColumn(String dadosAbertos) {
        if (dadosAbertos == null || dadosAbertos.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(dadosAbertos.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar dados bancários", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dadosCriptografados) {
        if (dadosCriptografados == null || dadosCriptografados.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dadosCriptografados)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar dados bancários", e);
        }
    }
}