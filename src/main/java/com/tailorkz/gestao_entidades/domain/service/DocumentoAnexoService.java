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

    public DocumentoAnexoService(DocumentoAnexoRepository anexoRepository,
                                 DespesaRepository despesaRepository,
                                 ArmazenamentoArquivoService armazenamentoService) {
        this.anexoRepository = anexoRepository;
        this.despesaRepository = despesaRepository;
        this.armazenamentoService = armazenamentoService;
    }

    @Transactional
    public DocumentoAnexo anexarArquivo(UUID despesaId, TipoDocumento tipo, MultipartFile arquivo) {
        // Verifica se a despesa existe no banco
        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada!"));

        // 2. Salva o arquivo fisicamente no disco
        String caminhoSalvo = armazenamentoService.armazenar(arquivo, arquivo.getOriginalFilename());

        // 3. Registra no banco de dados
        DocumentoAnexo novoAnexo = new DocumentoAnexo();
        novoAnexo.setDespesa(despesa);
        novoAnexo.setTipo(tipo);

        // Como ainda está no ambiente local, guarda o caminho da pasta aqui.
        novoAnexo.setUrlS3(caminhoSalvo);
        novoAnexo.setChaveS3(arquivo.getOriginalFilename());

        return anexoRepository.save(novoAnexo);
    }
}