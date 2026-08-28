package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada!"));

        // 1. Salva o arquivo fisicamente no disco (Pode ter até 30MB)
        String caminhoSalvo = armazenamentoService.armazenar(arquivo, arquivo.getOriginalFilename());

        // Aciona a compressão
        // Se for PDF, ele espreme e salva por cima. Se for imagem, ele ignora.
        pdfCompressorService.comprimirPdf(caminhoSalvo);

        // 3. Registra no banco de dados com o caminho já otimizado
        DocumentoAnexo novoAnexo = new DocumentoAnexo();
        novoAnexo.setDespesa(despesa);
        novoAnexo.setTipo(tipo);
        novoAnexo.setUrlS3(caminhoSalvo);
        novoAnexo.setChaveS3(arquivo.getOriginalFilename());

        return anexoRepository.save(novoAnexo);
    }
}