package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public DashboardController(UsuarioRepository usuarioRepository, DespesaRepository despesaRepository, ParcelaRepository parcelaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.despesaRepository = despesaRepository;
        this.parcelaRepository = parcelaRepository;
    }

    @GetMapping("/resumo/{parcelaId}")
    public ResponseEntity<DashboardResumoDTO> obterResumo(@PathVariable UUID parcelaId) {

        // 1. Pega a parcela para ver o financeiro
        Parcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        // 2. Busca TODOS os instrutores cadastrados no banco
        List<Usuario> todosInstrutores = usuarioRepository.findAll().stream()
                .filter(u -> "INSTRUTOR".equals(u.getRole().name()))
                .toList();

        // 3. Busca as Prestações Reais já enviadas para esta parcela
        List<Despesa> despesas = despesaRepository.findByParcelaId(parcelaId);

        // 4. Extrai apenas os IDs de quem já enviou
        List<UUID> idsQueEnviaram = despesas.stream()
                .map(d -> d.getUsuario().getId())
                .toList();

        // 5. A Mágica: Filtra os instrutores e converte a Categoria para String!
        List<InstrutorPendenteDTO> pendentes = todosInstrutores.stream()
                .filter(u -> !idsQueEnviaram.contains(u.getId()))
                .map(u -> new InstrutorPendenteDTO(
                        u.getId(),
                        u.getNome(),
                        u.getCategoria() != null ? u.getCategoria().name() : "Não definida" // <-- A CORREÇÃO ESTÁ AQUI
                ))
                .toList();

        // 6. Calcula a % de uso da parcela (Saúde Financeira)
        BigDecimal totalGasto = parcela.getValorInicial().subtract(parcela.getSaldoAtual());
        double saude = 0.0;
        if (parcela.getValorInicial().compareTo(BigDecimal.ZERO) > 0) {
            saude = totalGasto.divide(parcela.getValorInicial(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();
        }

        // 7. Empacota tudo e manda pro React
        DashboardResumoDTO resumo = new DashboardResumoDTO(
                todosInstrutores.size(),
                pendentes.size(),
                despesas.size(),
                (int) saude,
                pendentes
        );

        return ResponseEntity.ok(resumo);
    }
}

// DTOs auxiliares para formatar a resposta
record InstrutorPendenteDTO(UUID id, String nome, String categoria) {}
record DashboardResumoDTO(int totalInstrutores, int instrutoresPendentes, int prestacoesRecebidas, int saudeParcela, List<InstrutorPendenteDTO> listaPendentes) {}