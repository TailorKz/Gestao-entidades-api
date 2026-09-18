package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentoAnexoService {

    private final DocumentoAnexoRepository anexoRepository;
    private final DespesaRepository despesaRepository;
    private final ArmazenamentoArquivoService armazenamentoService;
    private final PdfCompressorService pdfCompressorService;

    public DocumentoAnexoService(DocumentoAnexoRepository anexoRepository,
                                 DespesaRepository despesaRepository,
                                 ArmazenamentoArquivoService armazenamentoService,
                                 PdfCompressorService pdfCompressorService) {
        this.anexoRepository = anexoRepository;
        this.despesaRepository = despesaRepository;
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

    private DocumentoAnexo anexarInterno(UUID despesaId, TipoDocumento tipo, String nomeOriginal, MultipartFile arquivo) {
        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada!"));

        // 1. Salva o arquivo fisicamente no disco (Pode ter até 30MB)
        String caminhoSalvo = armazenamentoService.armazenar(arquivo, nomeOriginal);

        // Aciona a compressão
        // Se for PDF, ele espreme e salva por cima. Se for imagem, ele ignora.
        pdfCompressorService.comprimirPdf(caminhoSalvo);

        // 3. Registra no banco de dados com o caminho já otimizado
        DocumentoAnexo novoAnexo = new DocumentoAnexo();
        novoAnexo.setDespesa(despesa);
        novoAnexo.setTipo(tipo);
        novoAnexo.setUrlS3(caminhoSalvo);
        novoAnexo.setChaveS3(nomeOriginal);

        return anexoRepository.save(novoAnexo);
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