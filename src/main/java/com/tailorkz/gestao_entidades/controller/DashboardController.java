package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final UsuarioRepository usuarioRepository;
    private final DespesaRepository despesaRepository;
    private final ParcelaRepository parcelaRepository;
    private final SegurancaService segurancaService;

    public DashboardController(UsuarioRepository usuarioRepository,
                               DespesaRepository despesaRepository,
                               ParcelaRepository parcelaRepository,
                               SegurancaService segurancaService) {
        this.usuarioRepository = usuarioRepository;
        this.despesaRepository = despesaRepository;
        this.parcelaRepository = parcelaRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping("/resumo/{parcelaId}")
    public ResponseEntity<DashboardResumoDTO> obterResumo(@PathVariable UUID parcelaId) {

        Parcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Parcela não encontrada"));

        segurancaService.garantirAcessoTenant(parcela.getFomento().getTenant().getId());
        UUID tenantParcela = parcela.getFomento().getTenant().getId();
        com.tailorkz.gestao_entidades.domain.enums.Categoria categoriaParcela = parcela.getFomento().getCategoria();

        // Apenas os instrutores da entidade dona da parcela (e do mesmo setor, quando houver)
        List<Usuario> instrutores = categoriaParcela != null
                ? usuarioRepository.findByTenantIdAndRoleAndCategoria(tenantParcela, Role.INSTRUTOR, categoriaParcela)
                : usuarioRepository.findByTenantIdAndRole(tenantParcela, Role.INSTRUTOR);

        List<Despesa> despesas = despesaRepository.findByParcelaId(parcelaId);

        List<UUID> idsQueEnviaram = despesas.stream()
                .map(d -> d.getUsuario().getId())
                .toList();

        List<InstrutorPendenteDTO> pendentes = instrutores.stream()
                .filter(u -> !idsQueEnviaram.contains(u.getId()))
                .map(u -> new InstrutorPendenteDTO(
                        u.getId(),
                        u.getNome(),
                        u.getCategoria() != null ? u.getCategoria().name() : "Não definida"
                ))
                .toList();

        BigDecimal totalGasto = parcela.getValorInicial().subtract(parcela.getSaldoAtual());
        double saude = 0.0;
        if (parcela.getValorInicial().compareTo(BigDecimal.ZERO) > 0) {
            saude = totalGasto.divide(parcela.getValorInicial(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();
        }

        DashboardResumoDTO resumo = new DashboardResumoDTO(
                instrutores.size(),
                pendentes.size(),
                despesas.size(),
                (int) saude,
                pendentes
        );

        return ResponseEntity.ok(resumo);
    }
}

record InstrutorPendenteDTO(UUID id, String nome, String categoria) {}
record DashboardResumoDTO(int totalInstrutores, int instrutoresPendentes, int prestacoesRecebidas, int saudeParcela, List<InstrutorPendenteDTO> listaPendentes) {}