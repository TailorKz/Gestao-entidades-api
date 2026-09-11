package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.AnexoDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaResponseDTO;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.domain.service.DespesaService;
import com.tailorkz.gestao_entidades.domain.service.DocumentoAnexoService;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    private final DocumentoAnexoService anexoService;
    private final DocumentoAnexoRepository documentoAnexoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SegurancaService segurancaService;

    public DespesaController(DespesaService despesaService,
                             DespesaRepository despesaRepository,
                             DocumentoAnexoService anexoService,
                             DocumentoAnexoRepository documentoAnexoRepository,
                             UsuarioRepository usuarioRepository,
                             SegurancaService segurancaService) {
        this.despesaService = despesaService;
        this.despesaRepository = despesaRepository;
        this.anexoService = anexoService;
        this.documentoAnexoRepository = documentoAnexoRepository;
        this.usuarioRepository = usuarioRepository;
        this.segurancaService = segurancaService;
    }

    @PostMapping
    public ResponseEntity<Despesa> registrarDespesa(@RequestBody DespesaRequestDTO dto) {
        Parcela parcela = validarParcelaAutenticada(dto.parcelaId());
        Usuario usuario = validarUsuarioAutenticado(dto.usuarioId());

        Despesa novaDespesa = new Despesa();
        novaDespesa.setValor(dto.valor());
        novaDespesa.setDataCompetencia(YearMonth.parse(dto.dataCompetencia()));
        novaDespesa.setStatus(StatusDespesa.AGUARDANDO_DOCUMENTOS);
        novaDespesa.setParcela(parcela);
        novaDespesa.setUsuario(usuario);

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);
        return ResponseEntity.status(HttpStatus.CREATED).body(despesaSalva);
    }

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

        BigDecimal valor = parseValor(valorString);
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        Usuario usuario = validarUsuarioAutenticado(usuarioId);

        Despesa novaDespesa = new Despesa();
        novaDespesa.setValor(valor);
        novaDespesa.setDataCompetencia(YearMonth.parse(dataCompetencia));
        novaDespesa.setStatus(StatusDespesa.PRONTA_PARA_MATCH);
        novaDespesa.setEmitente(emitente);
        novaDespesa.setDataEmissao(LocalDate.parse(dataEmissao));
        novaDespesa.setNumeroDocumento(numero);
        novaDespesa.setDescricao(descricao);
        novaDespesa.setParcela(parcela);
        novaDespesa.setUsuario(usuario);

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);

        anexar(despesaSalva.getId(), TipoDocumento.NOTA_FISCAL, notaFiscal);
        if (anexosExtras != null) {
            for (MultipartFile extra : anexosExtras) {
                anexar(despesaSalva.getId(), TipoDocumento.RELATORIO, extra);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(despesaSalva);
    }

    @GetMapping("/parcela/{parcelaId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarPorParcela(@PathVariable UUID parcelaId) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        List<DespesaResponseDTO> despesas = despesaRepository.findByParcelaId(parcela.getId()).stream()
                .map(d -> new DespesaResponseDTO(
                        d.getId(),
                        d.getValor(),
                        d.getDataCompetencia().toString(),
                        d.getStatus().name(),
                        d.getUsuario().getNome()
                )).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarPorUsuario(@PathVariable UUID usuarioId) {
        Usuario alvo = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        Usuario logado = segurancaService.logado();
        if (logado.getRole() == Role.INSTRUTOR && !logado.getId().equals(alvo.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        segurancaService.garantirAcessoTenant(alvo.getTenant().getId());

        List<DespesaResponseDTO> despesas = despesaRepository.findByUsuarioId(alvo.getId()).stream()
                .map(d -> new DespesaResponseDTO(
                        d.getId(),
                        d.getValor(),
                        d.getDataCompetencia().toString(),
                        d.getStatus().name(),
                        d.getUsuario().getNome()
                )).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/{despesaId}/anexos")
    public ResponseEntity<List<AnexoDTO>> listarAnexosDaDespesa(@PathVariable UUID despesaId) {
        Despesa despesa = buscarDespesa(despesaId);
        validarAcessoDespesa(despesa);

        List<AnexoDTO> anexos = documentoAnexoRepository.findByDespesaId(despesa.getId()).stream()
                .map(a -> new AnexoDTO(
                        a.getId(),
                        a.getTipo().name(),
                        a.getChaveS3(),
                        a.getUrlS3()
                )).toList();
        return ResponseEntity.ok(anexos);
    }

    @PatchMapping("/{despesaId}/status")
    public ResponseEntity<?> avancarStatus(@PathVariable UUID despesaId, @RequestBody AtualizarStatusDespesaDTO dto) {
        if (segurancaService.logado().getRole() == Role.INSTRUTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente gestores podem alterar o status da prestação.");
        }

        Despesa despesa = buscarDespesa(despesaId);
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());

        StatusDespesa atual = despesa.getStatus();
        StatusDespesa destino = dto.novoStatus();
        if (destino == null || destino.ordinal() <= atual.ordinal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transição inválida de status.");
        }

        if (destino != StatusDespesa.ENVIADA_GERR) {
            boolean temNota = documentoAnexoRepository.existsByDespesaIdAndTipo(despesa.getId(), TipoDocumento.NOTA_FISCAL);
            if (!temNota) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anexe a Nota Fiscal antes de avançar o status.");
            }
        }

        despesa.setStatus(destino);
        Despesa salva = despesaRepository.save(despesa);
        return ResponseEntity.ok(new DespesaResponseDTO(
                salva.getId(),
                salva.getValor(),
                salva.getDataCompetencia().toString(),
                salva.getStatus().name(),
                salva.getUsuario().getNome()
        ));
    }

    private Parcela validarParcelaAutenticada(UUID parcelaId) {
        if (segurancaService.ehSuperAdmin()) {
            return despesaService.buscarParcela(parcelaId);
        }
        return despesaService.parcelaDoTenant(parcelaId, segurancaService.tenantDoLogado());
    }

    private Usuario validarUsuarioAutenticado(UUID usuarioId) {
        Usuario logado = segurancaService.logado();
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (logado.getRole() == Role.INSTRUTOR && !logado.getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode registrar despesas em seu próprio nome.");
        }
        segurancaService.garantirAcessoTenant(usuario.getTenant().getId());
        return usuario;
    }

    private Despesa buscarDespesa(UUID despesaId) {
        return despesaRepository.findById(despesaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada."));
    }

    private void validarAcessoDespesa(Despesa despesa) {
        Usuario logado = segurancaService.logado();
        if (logado.getRole() == Role.INSTRUTOR && !logado.getId().equals(despesa.getUsuario().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());
    }

    private void anexar(UUID despesaId, TipoDocumento tipo, MultipartFile arquivo) {
        if (arquivo != null && !arquivo.isEmpty()) {
            anexoService.anexarArquivo(despesaId, tipo, arquivo);
        }
    }

    private BigDecimal parseValor(String valorString) {
        try {
            String valorLimpo = valorString.replace(".", "").replace(",", ".");
            return new BigDecimal(valorLimpo);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido.");
        }
    }
}

record AtualizarStatusDespesaDTO(StatusDespesa novoStatus) {}