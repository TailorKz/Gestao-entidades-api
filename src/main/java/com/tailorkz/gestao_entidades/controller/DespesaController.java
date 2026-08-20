package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.DespesaRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaResponseDTO;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.service.DespesaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/despesas")
@CrossOrigin(origins = "*")
public class DespesaController {

    private final DespesaService despesaService;
    private final DespesaRepository despesaRepository;

    public DespesaController(DespesaService despesaService, DespesaRepository despesaRepository) {
        this.despesaService = despesaService;
        this.despesaRepository = despesaRepository;
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