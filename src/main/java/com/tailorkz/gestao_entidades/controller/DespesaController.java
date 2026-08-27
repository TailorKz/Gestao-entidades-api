package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.DespesaRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaResponseDTO;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.service.DespesaService;
import com.tailorkz.gestao_entidades.domain.service.DocumentoAnexoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/despesas")
@CrossOrigin(origins = "*")
public class DespesaController {

    private final DespesaService despesaService;
    private final DespesaRepository despesaRepository;
    private final DocumentoAnexoService anexoService; // Injetando seu service de arquivos

    public DespesaController(DespesaService despesaService, DespesaRepository despesaRepository, DocumentoAnexoService anexoService) {
        this.despesaService = despesaService;
        this.despesaRepository = despesaRepository;
        this.anexoService = anexoService;
    }

    @PostMapping
    public ResponseEntity<Despesa> registrarDespesa(@RequestBody DespesaRequestDTO dto) {
        Despesa novaDespesa = new Despesa();
        novaDespesa.setValor(dto.valor());
        novaDespesa.setDataCompetencia(YearMonth.parse(dto.dataCompetencia()));
        novaDespesa.setStatus(StatusDespesa.AGUARDANDO_DOCUMENTOS);

        Parcela parcela = new Parcela();
        parcela.setId(dto.parcelaId());
        novaDespesa.setParcela(parcela);

        Usuario usuario = new Usuario();
        usuario.setId(dto.usuarioId());
        novaDespesa.setUsuario(usuario);

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);
        return ResponseEntity.status(HttpStatus.CREATED).body(despesaSalva);
    }

    // ENDPOINT ROTA (DADOS + ANEXOS) ---
    @PostMapping(value = "/com-anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Despesa> registrarComAnexos(
            @RequestParam("parcelaId") UUID parcelaId,
            @RequestParam("usuarioId") UUID usuarioId,
            @RequestParam("dataCompetencia") String dataCompetencia,
            @RequestParam("valor") String valorString,
            @RequestParam("emitente") String emitente,
            @RequestParam("dataEmissao") String dataEmissao,
            @RequestParam("numero") String numero,
            @RequestParam("descricao") String descricao,
            @RequestParam("notaFiscal") MultipartFile notaFiscal,
            @RequestParam(value = "anexosExtras", required = false) List<MultipartFile> anexosExtras) {

        String valorLimpo = valorString.replace(".", "").replace(",", ".");
        BigDecimal valor = new BigDecimal(valorLimpo);

        // 2. Montar Entidade
        Despesa novaDespesa = new Despesa();
        novaDespesa.setValor(valor);
        novaDespesa.setDataCompetencia(YearMonth.parse(dataCompetencia));
        novaDespesa.setStatus(StatusDespesa.PRONTA_PARA_MATCH); // Como enviou os arquivos, atualiza o status
        novaDespesa.setEmitente(emitente);
        novaDespesa.setDataEmissao(LocalDate.parse(dataEmissao));
        novaDespesa.setNumeroDocumento(numero);
        novaDespesa.setDescricao(descricao);

        Parcela parcela = new Parcela();
        parcela.setId(parcelaId);
        novaDespesa.setParcela(parcela);

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        novaDespesa.setUsuario(usuario);

        // 3. Salvar no Banco (service já abate o saldo da Parcela)
        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);

        // 4. Salvar Nota Fiscal no Disco via seu Service e vincular ao BD
        if (notaFiscal != null && !notaFiscal.isEmpty()) {
            anexoService.anexarArquivo(despesaSalva.getId(), TipoDocumento.NOTA_FISCAL, notaFiscal);
        }

        // 5. Salvar Anexos Extras no Disco via Service e vincular ao BD
        if (anexosExtras != null && !anexosExtras.isEmpty()) {
            for (MultipartFile extra : anexosExtras) {
                anexoService.anexarArquivo(despesaSalva.getId(), TipoDocumento.RELATORIO, extra);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(despesaSalva);
    }

    @GetMapping("/parcela/{parcelaId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarPorParcela(@PathVariable UUID parcelaId) {
        List<DespesaResponseDTO> despesas = despesaRepository.findByParcelaId(parcelaId).stream()
                .map(d -> new DespesaResponseDTO(
                        d.getId(),
                        d.getValor(),
                        d.getDataCompetencia().toString(),
                        d.getStatus().name(),
                        d.getUsuario().getNome()
                )).toList();
        return ResponseEntity.ok(despesas);
    }
}