package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.AcaoGerr;
import com.tailorkz.gestao_entidades.domain.repository.AcaoGerrRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/acoes-gerr")
@CrossOrigin(origins = "*")
public class AcaoGerrController {

    private final AcaoGerrRepository acaoGerrRepository;
    private final SegurancaService segurancaService;

    public AcaoGerrController(AcaoGerrRepository acaoGerrRepository, SegurancaService segurancaService) {
        this.acaoGerrRepository = acaoGerrRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping
    public ResponseEntity<List<AcaoGerrDTO>> listar(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "incluirInativas", defaultValue = "false") boolean incluirInativas) {
        Categoria cat = parseCategoria(categoria);
        List<AcaoGerr> acoes = (cat == null)
                ? acaoGerrRepository.findAll()
                : (incluirInativas
                        ? acaoGerrRepository.findByCategoriaOrderByPosicaoAsc(cat)
                        : acaoGerrRepository.findByCategoriaAndAtivoTrueOrderByPosicaoAsc(cat));

        List<AcaoGerrDTO> ordenadas = acoes.stream()
                .sorted(java.util.Comparator.comparing(AcaoGerr::getPosicao, java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(a -> new AcaoGerrDTO(a.getId(), a.getCategoria().name(), a.getNome(), a.getPosicao(), a.isAtivo()))
                .toList();
        return ResponseEntity.ok(ordenadas);
    }

    @PostMapping
    public ResponseEntity<AcaoGerrDTO> criar(@RequestBody AcaoGerrRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar as ações.");
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da ação.");
        }
        Categoria cat = parseCategoria(dto.categoria());
        if (cat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a categoria (ESPORTE ou CULTURA).");
        }

        AcaoGerr acao = new AcaoGerr();
        acao.setCategoria(cat);
        acao.setNome(dto.nome().trim());
        acao.setPosicao(acaoGerrRepository.maxPosicaoDaCategoria(cat) + 1);
        acao.setAtivo(true);
        acaoGerrRepository.save(acao);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(acao));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcaoGerrDTO> editar(@PathVariable UUID id, @RequestBody AcaoGerrEditDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar as ações.");
        AcaoGerr acao = acaoGerrRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação não encontrada."));

        if (dto.nome() != null && !dto.nome().isBlank()) {
            acao.setNome(dto.nome().trim());
        }
        if (dto.ativo() != null) {
            acao.setAtivo(dto.ativo());
        }
        acaoGerrRepository.save(acao);
        return ResponseEntity.ok(toDTO(acao));
    }

    @PostMapping("/reordenar")
    public ResponseEntity<List<AcaoGerrDTO>> reordenar(@RequestBody ReordenarAcoesDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem gerenciar as ações.");
        Categoria cat = parseCategoria(dto.categoria());
        if (cat == null || dto.ids() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a categoria e a ordem das ações.");
        }
        int pos = 1;
        for (UUID id : dto.ids()) {
            java.util.Optional<AcaoGerr> opcional = acaoGerrRepository.findById(id);
            if (opcional.isPresent() && opcional.get().getCategoria().equals(cat)) {
                opcional.get().setPosicao(pos++);
                acaoGerrRepository.save(opcional.get());
            }
        }
        List<AcaoGerrDTO> atualizadas = acaoGerrRepository.findByCategoriaOrderByPosicaoAsc(cat).stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(atualizadas);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem excluir ações.");
        AcaoGerr acao = acaoGerrRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação não encontrada."));
        acao.setAtivo(false);
        acaoGerrRepository.save(acao);
        return ResponseEntity.noContent().build();
    }

    private AcaoGerrDTO toDTO(AcaoGerr acao) {
        return new AcaoGerrDTO(acao.getId(), acao.getCategoria().name(), acao.getNome(), acao.getPosicao(), acao.isAtivo());
    }

    private Categoria parseCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return null;
        try {
            return Categoria.valueOf(categoria.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

record AcaoGerrDTO(UUID id, String categoria, String nome, Integer posicao, boolean ativo) {}
record AcaoGerrRequestDTO(String categoria, String nome) {}
record AcaoGerrEditDTO(String nome, Boolean ativo) {}
record ReordenarAcoesDTO(String categoria, List<UUID> ids) {}