package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.GrupoPrestacaoDTO;
import com.tailorkz.gestao_entidades.controller.dto.RelatorioPrestacaoDTO;
import com.tailorkz.gestao_entidades.domain.enums.TipoMovimentoBancario;
import com.tailorkz.gestao_entidades.domain.model.ContaBancaria;
import com.tailorkz.gestao_entidades.domain.model.GrupoPrestacao;
import com.tailorkz.gestao_entidades.domain.model.TransacaoBancaria;
import com.tailorkz.gestao_entidades.domain.service.PrestacaoBancariaService;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/prestacao-bancos")
@CrossOrigin(origins = "*")
public class PrestacaoBancariaController {

    private final PrestacaoBancariaService service;
    private final SegurancaService segurancaService;

    public PrestacaoBancariaController(PrestacaoBancariaService service, SegurancaService segurancaService) {
        this.service = service;
        this.segurancaService = segurancaService;
    }

    // ==============================
    // CONTAS
    // ==============================

    @GetMapping("/contas")
    public ResponseEntity<List<PrestacaoContaDTO>> listarContas() {
        return ResponseEntity.ok(service.listarContas().stream().map(this::toDTO).toList());
    }

    @PostMapping("/contas")
    public ResponseEntity<PrestacaoContaDTO> criarConta(@RequestBody PrestacaoContaRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar contas bancárias.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDTO(service.criarConta(dto.banco(), dto.finalidade())));
    }

    @PutMapping("/contas/{id}")
    public ResponseEntity<PrestacaoContaDTO> editarConta(@PathVariable UUID id, @RequestBody PrestacaoContaRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar contas bancárias.");
        return ResponseEntity.ok(toDTO(service.editarConta(id, dto.banco(), dto.finalidade())));
    }

    @DeleteMapping("/contas/{id}")
    public ResponseEntity<Void> excluirConta(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar contas bancárias.");
        service.excluirConta(id);
        return ResponseEntity.noContent().build();
    }

    // ==============================
    // TRANSAÇÕES / IMPORT OFX
    // ==============================

    @PostMapping("/contas/{id}/importar-ofx")
    public ResponseEntity<Map<String, Object>> importarOfx(@PathVariable UUID id,
                                                           @RequestParam("file") MultipartFile file) {
        segurancaService.garantirEhGestor("Somente gestores podem importar extratos bancários.");
        int importadas = service.importarOfx(id, file);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("importadas", importadas);
        resposta.put("mensagem", importadas > 0
                ? "Extrato importado com sucesso: " + importadas + " lançamento(s)."
                : "Nenhum lançamento encontrado no arquivo.");
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/contas/{id}/transacoes")
    public ResponseEntity<List<TransacaoBancariaDTO>> listarTransacoes(@PathVariable UUID id,
                                                                       @RequestParam int mes,
                                                                       @RequestParam int ano) {
        return ResponseEntity.ok(service.listarTransacoes(id, mes, ano).stream().map(this::toDTO).toList());
    }

    @PostMapping("/contas/{id}/transacoes/manual")
    public ResponseEntity<TransacaoBancariaDTO> adicionarManual(@PathVariable UUID id,
                                                                @RequestBody TransacaoManualRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem lançar transações manuais.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDTO(service.adicionarManual(id, parseData(dto.data()), dto.descricao(), parseTipo(dto.tipo()), dto.valor())));
    }

    @DeleteMapping("/transacoes/{id}")
    public ResponseEntity<Void> excluirTransacao(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem excluir transações.");
        service.excluirTransacao(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/contas/{id}/transacoes/limpar")
    public ResponseEntity<Void> limparMes(@PathVariable UUID id,
                                          @RequestParam int mes,
                                          @RequestParam int ano) {
        segurancaService.garantirEhGestor("Somente gestores podem apagar um mês inteiro.");
        service.limparMes(id, mes, ano);
        return ResponseEntity.noContent().build();
    }

    // ==============================
    // GRUPOS DE PRESTAÇÃO
    // ==============================

    @GetMapping("/contas/{id}/grupos")
    public ResponseEntity<List<GrupoPrestacaoDTO>> listarGrupos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarGrupos(id).stream().map(this::toGrupoDTO).toList());
    }

    @PostMapping("/contas/{id}/grupos")
    public ResponseEntity<GrupoPrestacaoDTO> criarGrupo(@PathVariable UUID id,
                                                        @RequestBody GrupoPrestacaoRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar grupos.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toGrupoDTO(service.criarGrupo(id, dto.nome(), dto.cor())));
    }

    @PutMapping("/contas/{id}/grupos/{grupoId}")
    public ResponseEntity<GrupoPrestacaoDTO> editarGrupo(@PathVariable UUID id,
                                                         @PathVariable UUID grupoId,
                                                         @RequestBody GrupoPrestacaoRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar grupos.");
        return ResponseEntity.ok(toGrupoDTO(service.editarGrupo(id, grupoId, dto.nome(), dto.cor())));
    }

    @DeleteMapping("/contas/{id}/grupos/{grupoId}")
    public ResponseEntity<Void> excluirGrupo(@PathVariable UUID id, @PathVariable UUID grupoId) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar grupos.");
        service.excluirGrupo(id, grupoId);
        return ResponseEntity.noContent().build();
    }

    // Classifica vários lançamentos de uma vez (grupoId null remove a classificação).
    @PostMapping("/transacoes/classificar")
    public ResponseEntity<Map<String, Object>> classificar(@RequestBody ClassificarTransacoesRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem classificar lançamentos.");
        int classificadas = service.classificar(dto.ids(), dto.grupoId());
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("classificadas", classificadas);
        resposta.put("mensagem", classificadas + " lançamento(s) classificados.");
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/contas/{id}/relatorio")
    public ResponseEntity<RelatorioPrestacaoDTO> relatorio(@PathVariable UUID id,
                                                           @RequestParam int ano) {
        return ResponseEntity.ok(service.relatorioAno(id, ano));
    }

    // ==============================
    // CONVERSÕES
    // ==============================

    private PrestacaoContaDTO toDTO(ContaBancaria conta) {
        return new PrestacaoContaDTO(conta.getId(), conta.getBanco(), conta.getFinalidade(), conta.getOrdem());
    }

    private GrupoPrestacaoDTO toGrupoDTO(GrupoPrestacao grupo) {
        return new GrupoPrestacaoDTO(grupo.getId(), grupo.getNome(), grupo.getCor(), grupo.getOrdem());
    }

    private TransacaoBancariaDTO toDTO(TransacaoBancaria transacao) {
        return new TransacaoBancariaDTO(
                transacao.getId(),
                transacao.getData().toString(),
                transacao.getDescricao(),
                transacao.getTipo().name(),
                transacao.getValor(),
                transacao.getGrupo() != null ? transacao.getGrupo().getId() : null,
                transacao.getGrupo() != null ? transacao.getGrupo().getNome() : null,
                transacao.getGrupo() != null ? transacao.getGrupo().getCor() : null
        );
    }

    private static TipoMovimentoBancario parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return null;
        try {
            return TipoMovimentoBancario.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo inválido. Use ENTRADA ou SAIDA.");
        }
    }

    private static LocalDate parseData(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            return LocalDate.parse(data);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Data inválida. Use o formato yyyy-MM-dd.");
        }
    }
}

record PrestacaoContaDTO(UUID id, String banco, String finalidade, Integer ordem) {}

record PrestacaoContaRequestDTO(String banco, String finalidade) {}

record GrupoPrestacaoRequestDTO(String nome, String cor) {}

record ClassificarTransacoesRequestDTO(List<UUID> ids, UUID grupoId) {}

record TransacaoBancariaDTO(UUID id, String data, String descricao, String tipo, BigDecimal valor,
                            UUID grupoId, String grupoNome, String grupoCor) {}

record TransacaoManualRequestDTO(String data, String descricao, String tipo, BigDecimal valor) {}