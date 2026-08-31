package com.tailorkz.gestao_entidades.domain.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";

    // CHAVE DE 16 BYTES (128 bits)
    // Nota: Em um ambiente de produção real, irá para o application.properties
    private static final byte[] KEY = "IndaciSecretKey!".getBytes();

    @Override
    public String convertToDatabaseColumn(String dadosAbertos) {
        if (dadosAbertos == null || dadosAbertos.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(KEY, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(dadosAbertos.getBytes()));
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
            return new String(cipher.doFinal(Base64.getDecoder().decode(dadosCriptografados)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar dados bancários", e);
        }
    }
}