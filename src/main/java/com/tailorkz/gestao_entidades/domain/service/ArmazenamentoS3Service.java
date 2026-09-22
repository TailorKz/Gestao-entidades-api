package com.tailorkz.gestao_entidades.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
@Primary
public class ArmazenamentoS3Service implements ArmazenamentoArquivoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public ArmazenamentoS3Service(
            @Value("${aws.s3.access-key}") String accessKey,
            @Value("${aws.s3.secret-key}") String secretKey,
            @Value("${aws.s3.region}") String regionString,
            @Value("${aws.s3.bucket}") String bucketName) {

        this.bucketName = bucketName;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        Region regiaoConfigurada = Region.of(regionString);
        Region regiaoReal = descobrirRegiaoDoS3(regiaoConfigurada, bucketName, credentials);

        this.s3Client = S3Client.builder()
                .region(regiaoReal)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        this.s3Presigner = S3Presigner.builder()
                .region(regiaoReal)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    // Descobre a região real do bucket para evitar o erro 301 "PermanentRedirect"
    private Region descobrirRegiaoDoS3(Region configurada, String bucket, AwsBasicCredentials credenciais) {
        try (S3Client probe = S3Client.builder()
                .region(configurada)
                .credentialsProvider(StaticCredentialsProvider.create(credenciais))
                .build()) {
            String localizacao = probe.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucket).build())
                    .locationConstraintAsString();
            if (localizacao == null || localizacao.isBlank() || "null".equalsIgnoreCase(localizacao)) {
                return Region.US_EAST_1;
            }
            return Region.of(localizacao);
        } catch (Exception e) {
            System.err.println("Não foi possível detectar a região do bucket '" + bucket
                    + "', usando a configurada (" + configurada + "): " + e.getMessage());
            return configurada;
        }
    }

    @Override
    public String armazenar(MultipartFile arquivo, String nomeArquivoOriginal) {
        throw new UnsupportedOperationException("Use o upload via Path para suportar a compressão.");
    }

    @Override
    public String armazenar(Path arquivoFisico, String nomeArquivoOriginal) {
        try {
            String nomeSeguro = UUID.randomUUID().toString() + "_" + nomeArquivoOriginal.replace(" ", "_");
            String contentType = nomeArquivoOriginal.toLowerCase().endsWith(".pdf") ? "application/pdf" : "application/octet-stream";

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(nomeSeguro)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putOb, arquivoFisico);

            // Retorna apenas a chave gerada para salvar no Postgres
            return nomeSeguro;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao enviar arquivo para o S3", e);
        }
    }

    @Override
    public void deletar(String chaveArquivo) {
        if (chaveArquivo == null || chaveArquivo.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(chaveArquivo).build());
        } catch (Exception e) {
            System.err.println("Erro ao deletar do S3: " + e.getMessage());
        }
    }

    // Gera um link temporário válido por 5 minutos
    public String gerarUrlPreAssinada(String chaveArquivo) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(chaveArquivo)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}