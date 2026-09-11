package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.DadosNotaDTO;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.service.DocumentoAnexoService;
import com.tailorkz.gestao_entidades.domain.service.OcrService;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/anexos")
@CrossOrigin(origins = "*")
public class AnexoController {

    private final DocumentoAnexoService anexoService;
    private final OcrService ocrService;
    private final DespesaRepository despesaRepository;
    private final SegurancaService segurancaService;

    public AnexoController(DocumentoAnexoService anexoService,
                           OcrService ocrService,
                           DespesaRepository despesaRepository,
                           SegurancaService segurancaService) {
        this.anexoService = anexoService;
        this.ocrService = ocrService;
        this.despesaRepository = despesaRepository;
        this.segurancaService = segurancaService;
    }

    @PostMapping
    public ResponseEntity<DocumentoAnexo> fazerUpload(
            @RequestParam("despesaId") UUID despesaId,
            @RequestParam("tipo") TipoDocumento tipo,
            @RequestParam("arquivo") MultipartFile arquivo) {

        validarAcesso(despesaId);
        DocumentoAnexo anexoSalvo = anexoService.anexarArquivo(despesaId, tipo, arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoSalvo);
    }

    @PostMapping("/ler-nota")
    public ResponseEntity<DadosNotaDTO> lerNotaFiscal(@RequestParam("arquivo") MultipartFile arquivo) {
        DadosNotaDTO dadosExtraidos = ocrService.extrairDadosPdf(arquivo);
        return ResponseEntity.ok(dadosExtraidos);
    }

    private void validarAcesso(UUID despesaId) {
        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada."));

        var logado = segurancaService.logado();
        if (logado.getRole() == Role.INSTRUTOR && !logado.getId().equals(despesa.getUsuario().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());
    }
}