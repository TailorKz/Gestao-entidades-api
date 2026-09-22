package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.repository.ComprovanteBbRepository;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentoAnexoService {

    // Arquivos abaixo deste tamanho não passam pela compressão (ganho pequeno x tempo gasto)
    private static final long LIMITE_COMPRIMIR = 300_000; // ~300KB

    private final DocumentoAnexoRepository anexoRepository;
    private final DespesaRepository despesaRepository;
    private final ComprovanteBbRepository comprovanteRepository;
    private final ArmazenamentoArquivoService armazenamentoService;
    private final PdfCompressorService pdfCompressorService;

    public DocumentoAnexoService(DocumentoAnexoRepository anexoRepository,
                                 DespesaRepository despesaRepository,
                                 ComprovanteBbRepository comprovanteRepository,
                                 ArmazenamentoArquivoService armazenamentoService,
                                 PdfCompressorService pdfCompressorService) {
        this.anexoRepository = anexoRepository;
        this.despesaRepository = despesaRepository;
        this.comprovanteRepository = comprovanteRepository;
        this.armazenamentoService = armazenamentoService;
        this.pdfCompressorService = pdfCompressorService;
    }

    @Transactional
    public DocumentoAnexo anexarArquivo(UUID despesaId, TipoDocumento tipo, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) return null;
        return anexarInterno(despesaId, tipo, arquivo.getOriginalFilename(), arquivo);
    }

    @Transactional
    public DocumentoAnexo anexarArquivo(UUID despesaId, TipoDocumento tipo, byte[] dados, String nomeArquivo) {
        if (dados == null || dados.length == 0) return null;
        return anexarInterno(despesaId, tipo, nomeArquivo, new BytesMultipartFile(nomeArquivo, dados));
    }

    // Envia os bytes direto para o S3 (via arquivo temporário + compressão) e retorna a chave gerada
    public String enviarParaArmazenamento(byte[] dados, String nomeArquivo) {
        try {
            java.nio.file.Path tempPath = java.nio.file.Files.createTempFile("upload_", nomeArquivo.replace(" ", "_"));
            java.nio.file.Files.write(tempPath, dados);
            if (java.nio.file.Files.size(tempPath) > LIMITE_COMPRIMIR) {
                pdfCompressorService.comprimirPdf(tempPath.toString());
            }
            String chaveS3 = armazenamentoService.armazenar(tempPath, nomeArquivo);
            java.nio.file.Files.deleteIfExists(tempPath);
            return chaveS3;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao enviar arquivo para o S3", e);
        }
    }

    // Vincula um comprovante que JÁ está no S3, sem reenviar os bytes
    @Transactional
    public DocumentoAnexo anexarExistente(UUID despesaId, TipoDocumento tipo, String chaveS3, String nomeOriginal) {
        if (chaveS3 == null || chaveS3.isBlank()) return null;
        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada!"));
        DocumentoAnexo novoAnexo = new DocumentoAnexo();
        novoAnexo.setDespesa(despesa);
        novoAnexo.setTipo(tipo);
        novoAnexo.setUrlS3(chaveS3);
        novoAnexo.setChaveS3(nomeOriginal);
        return anexoRepository.save(novoAnexo);
    }

    private DocumentoAnexo anexarInterno(UUID despesaId, TipoDocumento tipo, String nomeOriginal, MultipartFile arquivo) {
        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada!"));

        try {
            // 1. Cria um arquivo temporário local para permitir a compressão do PDFBox
            java.nio.file.Path tempPath = java.nio.file.Files.createTempFile("upload_", nomeOriginal.replace(" ", "_"));
            arquivo.transferTo(tempPath.toFile());

            // 2. Comprime o arquivo localmente (apenas se valer a pena)
            if (java.nio.file.Files.size(tempPath) > LIMITE_COMPRIMIR) {
                pdfCompressorService.comprimirPdf(tempPath.toString());
            }

            // 3. Envia o arquivo comprimido para o S3
            String chaveS3 = armazenamentoService.armazenar(tempPath, nomeOriginal);

            // 4. Apaga o lixo temporário do servidor (importante no Railway)
            java.nio.file.Files.deleteIfExists(tempPath);

            // 5. Salva no banco de dados apenas a chave e o nome
            DocumentoAnexo novoAnexo = new DocumentoAnexo();
            novoAnexo.setDespesa(despesa);
            novoAnexo.setTipo(tipo);
            novoAnexo.setUrlS3(chaveS3);
            novoAnexo.setChaveS3(nomeOriginal);
            return anexoRepository.save(novoAnexo);

        } catch (Exception e) {
            throw new RuntimeException("Falha ao processar e enviar anexo para o S3", e);
        }
    }

    // Apaga o objeto no S3 pelo nome da chave
    public void deletarArquivo(String chaveS3) {
        armazenamentoService.deletar(chaveS3);
    }

    // Exclui um anexo (nota, relatório, lista ou comprovante) e reorganiza status/vínculos
    @Transactional
    public void excluirAnexo(UUID anexoId) {
        DocumentoAnexo anexo = anexoRepository.findById(anexoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));

        Despesa despesa = anexo.getDespesa();
        TipoDocumento tipo = anexo.getTipo();

        anexoRepository.delete(anexo);
        armazenamentoService.deletar(anexo.getUrlS3());

        if (despesa == null) return;

        if (tipo == TipoDocumento.COMPROVANTE_PAGAMENTO) {
            // Devolve o comprovante BB para a lista de pendentes
            List<ComprovanteBb> comprovantes = comprovanteRepository.findByDespesaId(despesa.getId());
            for (ComprovanteBb c : comprovantes) {
                c.setDespesa(null);
                c.setVinculado(false);
                comprovanteRepository.save(c);
            }
        }

        recomputarStatus(despesa.getId());
    }

    private void recomputarStatus(UUID despesaId) {
        Despesa despesa = despesaRepository.findById(despesaId).orElse(null);
        if (despesa == null) return;
        boolean temNota = anexoRepository.existsByDespesaIdAndTipo(despesa.getId(), TipoDocumento.NOTA_FISCAL);
        boolean temComp = anexoRepository.existsByDespesaIdAndTipo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO);
        despesa.setStatus(temNota
                ? (temComp ? StatusDespesa.MATCH_REALIZADO : StatusDespesa.PRONTA_PARA_MATCH)
                : StatusDespesa.AGUARDANDO_DOCUMENTOS);
        despesaRepository.save(despesa);
    }

    @Transactional
    public void removerAnexosDaDespesa(UUID despesaId) {
        List<DocumentoAnexo> anexos = anexoRepository.findByDespesaId(despesaId);
        for (DocumentoAnexo anexo : anexos) {
            armazenamentoService.deletar(anexo.getUrlS3());
            anexoRepository.delete(anexo);
        }
    }

    static class BytesMultipartFile implements MultipartFile {
        private final String nome;
        private final byte[] dados;

        BytesMultipartFile(String nome, byte[] dados) {
            this.nome = nome;
            this.dados = dados;
        }

        @Override
        public String getName() { return nome; }

        @Override
        public String getOriginalFilename() { return nome; }

        @Override
        public String getContentType() { return "application/pdf"; }

        @Override
        public boolean isEmpty() { return dados.length == 0; }

        @Override
        public long getSize() { return dados.length; }

        @Override
        public byte[] getBytes() { return dados; }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(dados);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), dados);
        }
    }
}