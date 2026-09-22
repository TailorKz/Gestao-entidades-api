package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.Fomento;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.FomentoRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/parcelas")
@CrossOrigin(origins = "*")
public class ParcelaController {

    private final ParcelaRepository parcelaRepository;
    private final FomentoRepository fomentoRepository;
    private final SegurancaService segurancaService;

    public ParcelaController(ParcelaRepository parcelaRepository,
                             FomentoRepository fomentoRepository,
                             SegurancaService segurancaService) {
        this.parcelaRepository = parcelaRepository;
        this.fomentoRepository = fomentoRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping
    public ResponseEntity<List<ParcelaDTO>> listarTodas(@RequestParam(required = false) String categoria) {
        Categoria cat = parseCategoria(categoria);
        List<Parcela> parcelas;
        if (segurancaService.ehSuperAdmin()) {
            parcelas = cat != null
                    ? parcelaRepository.findAllByFomento_Categoria(cat)
                    : parcelaRepository.findAll();
        } else {
            UUID tenantId = segurancaService.tenantDoLogado();
            parcelas = cat != null
                    ? parcelaRepository.findByFomento_TenantIdAndFomento_Categoria(tenantId, cat)
                    : parcelaRepository.findByFomento_TenantId(tenantId);
        }

        List<ParcelaDTO> lista = parcelas.stream()
                .sorted(java.util.Comparator.comparing(Parcela::getNumero, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(p -> new ParcelaDTO(
                        p.getId(),
                        p.getFomento().getId(),
                        p.getFomento().getTitulo(),
                        p.getFomento().getAnoVigencia(),
                        p.getNumero(),
                        p.getValorInicial(),
                        p.getSaldoAtual(),
                        p.getMesesReferencia(),
                        p.getFomento().getCategoria() != null ? p.getFomento().getCategoria().name() : null
                )).toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<ParcelaDTO> criar(@RequestBody ParcelaRequestDTO dto) {
        Fomento fomento;
        if (dto.fomentoId() != null) {
            fomento = buscarFomentoTenant(dto.fomentoId());
            segurancaService.garantirAcessoTenant(fomento.getTenant().getId());
        } else {
            Categoria cat = parseCategoria(dto.categoria());
            if (segurancaService.ehSuperAdmin()) {
                fomento = cat != null
                        ? fomentoRepository.findAllByCategoria(cat).stream().findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum fomento encontrado para a categoria solicitada."))
                        : fomentoRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum fomento cadastrado no sistema."));
            } else {
                UUID tenantId = segurancaService.tenantDoLogado();
                fomento = cat != null
                        ? fomentoRepository.findFirstByTenantIdAndCategoria(tenantId, cat)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum fomento encontrado para a categoria nesta entidade."))
                        : fomentoRepository.findFirstByTenantId(tenantId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum fomento cadastrado para esta entidade."));
            }
        }

        Parcela p = new Parcela();
        p.setFomento(fomento);
        p.setNumero(dto.numero());
        p.setValorInicial(dto.valorInicial());
        p.setSaldoAtual(dto.valorInicial());
        p.setMesesReferencia(dto.mesesReferencia());

        Parcela salva = parcelaRepository.save(p);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ParcelaDTO(salva.getId(), fomento.getId(), fomento.getTitulo(), fomento.getAnoVigencia(),
                        salva.getNumero(), salva.getValorInicial(), salva.getSaldoAtual(), salva.getMesesReferencia(),
                        fomento.getCategoria() != null ? fomento.getCategoria().name() : null)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParcelaDTO> atualizar(@PathVariable UUID id, @RequestBody ParcelaUpdateDTO dto) {
        return parcelaRepository.findById(id).map(p -> {
            segurancaService.garantirAcessoTenant(p.getFomento().getTenant().getId());

            BigDecimal diferenca = dto.novoValorInicial().subtract(p.getValorInicial());
            BigDecimal novoSaldo = p.getSaldoAtual().add(diferenca);
            if (novoSaldo.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Novo valor reduziria a parcela abaixo do que já foi gasto.");
            }

            p.setValorInicial(dto.novoValorInicial());
            p.setSaldoAtual(novoSaldo);
            p.setMesesReferencia(dto.mesesReferencia());

            Parcela salva = parcelaRepository.save(p);

            return ResponseEntity.ok(new ParcelaDTO(
                    salva.getId(), salva.getFomento().getId(), salva.getFomento().getTitulo(), salva.getFomento().getAnoVigencia(),
                    salva.getNumero(), salva.getValorInicial(), salva.getSaldoAtual(), salva.getMesesReferencia(),
                    salva.getFomento().getCategoria() != null ? salva.getFomento().getCategoria().name() : null
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Fomento buscarFomentoTenant(UUID fomentoId) {
        return fomentoRepository.findById(fomentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fomento não encontrado."));
    }

    private Categoria parseCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return null;
        try {
            return Categoria.valueOf(categoria.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida: use ESPORTE ou CULTURA.");
        }
    }
}

record ParcelaDTO(UUID id, UUID fomentoId, String tituloFomento, Integer anoVigencia, Integer numero, BigDecimal valorInicial, BigDecimal saldoAtual, String mesesReferencia, String categoria) {}
record ParcelaRequestDTO(UUID fomentoId, Integer numero, BigDecimal valorInicial, String mesesReferencia, String categoria) {}
record ParcelaUpdateDTO(BigDecimal novoValorInicial, String mesesReferencia) {}