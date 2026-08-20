package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.DadosNotaDTO;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.service.DocumentoAnexoService;
import com.tailorkz.gestao_entidades.domain.service.OcrService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/anexos")
@CrossOrigin(origins = "*") // Libera o React para acessar este controller
public class AnexoController {

    private final DocumentoAnexoService anexoService;
    private final OcrService ocrService;

    // Construtor atualizado para receber o motor de OCR
    public AnexoController(DocumentoAnexoService anexoService, OcrService ocrService) {
        this.anexoService = anexoService;
        this.ocrService = ocrService;
    }

    @PostMapping
    public ResponseEntity<DocumentoAnexo> fazerUpload(
            @RequestParam("despesaId") UUID despesaId,
            @RequestParam("tipo") TipoDocumento tipo,
            @RequestParam("arquivo") MultipartFile arquivo) {

        DocumentoAnexo anexoSalvo = anexoService.anexarArquivo(despesaId, tipo, arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoSalvo);
    }

    // --- NOVA ROTA DO LEITOR ---
    @PostMapping("/ler-nota")
    public ResponseEntity<DadosNotaDTO> lerNotaFiscal(@RequestParam("arquivo") MultipartFile arquivo) {
        DadosNotaDTO dadosExtraidos = ocrService.extrairDadosPdf(arquivo);
        return ResponseEntity.ok(dadosExtraidos);
    }
}