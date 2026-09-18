package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.AnexoDTO;
import com.tailorkz.gestao_entidades.controller.dto.ComprovanteDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.DespesaResponseDTO;
import com.tailorkz.gestao_entidades.controller.dto.GerrPrestacaoDTO;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DespesaEstimada;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.ComprovanteBbRepository;
import com.tailorkz.gestao_entidades.domain.repository.DespesaEstimadaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.domain.service.ConciliacaoService;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
    private final ParcelaRepository parcelaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DespesaEstimadaRepository despesaEstimadaRepository;
    private final ComprovanteBbRepository comprovanteBbRepository;
    private final ConciliacaoService conciliacaoService;
    private final SegurancaService segurancaService;

    public DespesaController(DespesaService despesaService,
                             DespesaRepository despesaRepository,
                             DocumentoAnexoService anexoService,
                             DocumentoAnexoRepository documentoAnexoRepository,
                             ParcelaRepository parcelaRepository,
                             UsuarioRepository usuarioRepository,
                             DespesaEstimadaRepository despesaEstimadaRepository,
                             ComprovanteBbRepository comprovanteBbRepository,
                             ConciliacaoService conciliacaoService,
                             SegurancaService segurancaService) {
        this.despesaService = despesaService;
        this.despesaRepository = despesaRepository;
        this.anexoService = anexoService;
        this.documentoAnexoRepository = documentoAnexoRepository;
        this.parcelaRepository = parcelaRepository;
        this.usuarioRepository = usuarioRepository;
        this.despesaEstimadaRepository = despesaEstimadaRepository;
        this.comprovanteBbRepository = comprovanteBbRepository;
        this.conciliacaoService = conciliacaoService;
        this.segurancaService = segurancaService;
    }

    @PostMapping
    public ResponseEntity<DespesaResponseDTO> registrarDespesa(@RequestBody DespesaRequestDTO dto) {
        Parcela parcela = validarParcelaAutenticada(dto.parcelaId());
        Usuario usuario = validarUsuarioAutenticado(dto.usuarioId());

        Despesa novaDespesa = new Despesa();
        novaDespesa.setValor(dto.valor());
        novaDespesa.setDataCompetencia(YearMonth.parse(dto.dataCompetencia()));
        novaDespesa.setStatus(StatusDespesa.AGUARDANDO_DOCUMENTOS);
        novaDespesa.setParcela(parcela);
        novaDespesa.setUsuario(usuario);

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);
        return ResponseEntity.status(HttpStatus.CREATED).body(paraDTO(despesaSalva));
    }

    @PostMapping(value = "/com-anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DespesaResponseDTO> registrarComAnexos(
            @RequestParam("parcelaId") UUID parcelaId,
            @RequestParam("usuarioId") UUID usuarioId,
            @RequestParam("dataCompetencia") String dataCompetencia,
            @RequestParam("valor") String valorString,
            @RequestParam("emitente") String emitente,
            @RequestParam("dataEmissao") String dataEmissao,
            @RequestParam("numero") String numero,
            @RequestParam("descricao") String descricao,
            @RequestParam(value = "documentoFavorecido", required = false) String documentoFavorecido,
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
        novaDespesa.setDocumentoFavorecido(limparDocumento(documentoFavorecido));
        novaDespesa.setParcela(parcela);
        novaDespesa.setUsuario(usuario);

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);

        anexar(despesaSalva.getId(), TipoDocumento.NOTA_FISCAL, notaFiscal);
        if (anexosExtras != null) {
            for (MultipartFile extra : anexosExtras) {
                anexar(despesaSalva.getId(), TipoDocumento.RELATORIO, extra);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(paraDTO(despesaSalva));
    }

    @PostMapping(value = "/admin-lancar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DespesaResponseDTO> lancarPeloAdmin(
            @RequestParam("parcelaId") UUID parcelaId,
            @RequestParam(value = "usuarioId", required = false) UUID usuarioId,
            @RequestParam("dataCompetencia") String dataCompetencia,
            @RequestParam("valor") String valorString,
            @RequestParam("emitente") String emitente,
            @RequestParam("dataEmissao") String dataEmissao,
            @RequestParam("numero") String numero,
            @RequestParam("descricao") String descricao,
            @RequestParam(value = "nomeEmpresa", required = false) String nomeEmpresa,
            @RequestParam(value = "observacao", required = false) String observacao,
            @RequestParam(value = "documentoFavorecido", required = false) String documentoFavorecido,
            @RequestParam("notaFiscal") MultipartFile notaFiscal,
            @RequestParam(value = "anexosExtras", required = false) List<MultipartFile> anexosExtras) {

        segurancaService.garantirEhGestor("Somente gestores podem lançar despesas em nome de outros.");

        BigDecimal valor = parseValor(valorString);
        Parcela parcela = validarParcelaAutenticada(parcelaId);

        boolean avulso = usuarioId == null;
        if (avulso && (nomeEmpresa == null || nomeEmpresa.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da empresa para lançamento avulso.");
        }

        Usuario usuario = avulso ? segurancaService.logado() : validarUsuarioAutenticado(usuarioId);

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
        novaDespesa.setNomeEmpresa(avulso ? nomeEmpresa.trim() : null);
        novaDespesa.setObservacao(observacao != null && !observacao.isBlank() ? observacao.trim() : null);
        novaDespesa.setDocumentoFavorecido(limparDocumento(documentoFavorecido));

        Despesa despesaSalva = despesaService.registrarNovaDespesa(novaDespesa);

        if (avulso) {
            String descEstimativa = nomeEmpresa.trim();
            if (observacao != null && !observacao.isBlank()) {
                descEstimativa += " - OBS: " + observacao.trim();
            }
            DespesaEstimada estimativa = new DespesaEstimada();
            estimativa.setDescricao(descEstimativa);
            estimativa.setValor(valor);
            estimativa.setParcela(parcela);
            despesaEstimadaRepository.save(estimativa);
        }

        anexar(despesaSalva.getId(), TipoDocumento.NOTA_FISCAL, notaFiscal);
        if (anexosExtras != null) {
            for (MultipartFile extra : anexosExtras) {
                anexar(despesaSalva.getId(), TipoDocumento.RELATORIO, extra);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(paraDTO(despesaSalva));
    }

    @GetMapping("/parcela/{parcelaId}/instrutor/{instrutorId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarDoInstrutorNaParcela(
            @PathVariable UUID parcelaId,
            @PathVariable UUID instrutorId) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);

        Usuario instrutor = usuarioRepository.findById(instrutorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrutor não encontrado."));
        segurancaService.garantirAcessoTenant(instrutor.getTenant().getId());

        List<DespesaResponseDTO> despesas = despesaRepository.findByParcelaIdAndUsuarioId(parcela.getId(), instrutor.getId())
                .stream()
                .map(this::paraDTO).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/parcela/{parcelaId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarPorParcela(@PathVariable UUID parcelaId) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        List<DespesaResponseDTO> despesas = despesaRepository.findByParcelaId(parcela.getId()).stream()
                .map(this::paraDTO).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DespesaResponseDTO>> listarPorUsuario(@PathVariable UUID usuarioId) {
        Usuario alvo = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        segurancaService.garantirProprioOuGestor(alvo.getId(), alvo.getTenant().getId(), "Acesso negado.");

        List<DespesaResponseDTO> despesas = despesaRepository.findByUsuarioId(alvo.getId()).stream()
                .map(this::paraDTO).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/{despesaId}/anexos")
    public ResponseEntity<List<AnexoDTO>> listarAnexosDaDespesa(@PathVariable UUID despesaId) {
        Despesa despesa = buscarDespesa(despesaId);
        segurancaService.garantirAcessoDespesa(despesa);

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
        segurancaService.garantirEhGestor("Somente gestores podem alterar o status da prestação.");

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
        return ResponseEntity.ok(paraDTO(salva));
    }

    @PutMapping("/{despesaId}")
    public ResponseEntity<DespesaResponseDTO> editar(@PathVariable UUID despesaId, @RequestBody EditarDespesaDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem editar despesas.");

        Despesa despesa = buscarDespesa(despesaId);
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());

        if (dto.valor() == null || dto.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor da despesa deve ser maior que zero.");
        }

        Parcela parcela = despesa.getParcela();
        BigDecimal saldoAjustado = parcela.getSaldoAtual().add(despesa.getValor());
        if (dto.valor().compareTo(saldoAjustado) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor da despesa excede o saldo disponível da parcela (R$ " + saldoAjustado + ").");
        }

        despesa.setValor(dto.valor());
        if (dto.dataCompetencia() != null && !dto.dataCompetencia().isBlank()) {
            despesa.setDataCompetencia(YearMonth.parse(dto.dataCompetencia()));
        }
        if (dto.emitente() != null) despesa.setEmitente(dto.emitente().trim());
        if (dto.numero() != null) despesa.setNumeroDocumento(dto.numero().trim());
        if (dto.descricao() != null) despesa.setDescricao(dto.descricao().trim());
        if (dto.nomeEmpresa() != null && !dto.nomeEmpresa().trim().isEmpty()) {
            despesa.setNomeEmpresa(dto.nomeEmpresa().trim());
        }
        if (dto.observacao() != null) despesa.setObservacao(dto.observacao().isEmpty() ? null : dto.observacao().trim());
        if (dto.documentoFavorecido() != null) {
            despesa.setDocumentoFavorecido(limparDocumento(dto.documentoFavorecido()));
        }

        Despesa salva = despesaRepository.save(despesa);
        parcela.setSaldoAtual(saldoAjustado.subtract(dto.valor()));
        parcelaRepository.save(parcela);

        if (salva.getNomeEmpresa() != null && !salva.getNomeEmpresa().isBlank()) {
            String empresa = salva.getNomeEmpresa().trim();
            StringBuilder descEstimativa = new StringBuilder(empresa);
            if (salva.getObservacao() != null && !salva.getObservacao().isBlank()) {
                descEstimativa.append(" - OBS: ").append(salva.getObservacao().trim());
            }
            String descricaoFinal = descEstimativa.toString();
            despesaEstimadaRepository.findByParcelaId(parcela.getId()).stream()
                    .filter(e -> {
                        String d = e.getDescricao();
                        if (d == null) return false;
                        return d.equals(empresa)
                                || d.startsWith(empresa + " - OBS:")
                                || d.equals("Lançamento avulso \u2014 " + empresa); // legado
                    })
                    .findFirst()
                    .ifPresent(e -> {
                        e.setDescricao(descricaoFinal);
                        despesaEstimadaRepository.save(e);
                    });
        }

        return ResponseEntity.ok(paraDTO(salva));
    }

    @DeleteMapping("/{despesaId}")
    public ResponseEntity<Void> deletar(@PathVariable UUID despesaId) {
        segurancaService.garantirEhGestor("Somente gestores podem excluir despesas.");

        Despesa despesa = buscarDespesa(despesaId);
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());

        Parcela parcela = despesa.getParcela();
        parcela.setSaldoAtual(parcela.getSaldoAtual().add(despesa.getValor()));
        parcelaRepository.save(parcela);

        anexoService.removerAnexosDaDespesa(despesa.getId());

        if (despesa.getNomeEmpresa() != null && !despesa.getNomeEmpresa().isBlank()) {
            String empresa = despesa.getNomeEmpresa().trim();
            despesaEstimadaRepository.findByParcelaId(parcela.getId()).stream()
                    .filter(e -> {
                        String d = e.getDescricao();
                        if (d == null) return false;
                        return d.equals(empresa)
                                || d.startsWith(empresa + " - OBS:")
                                || d.equals("Lançamento avulso \u2014 " + empresa); // legado
                    })
                    .findFirst()
                    .ifPresent(despesaEstimadaRepository::delete);
        }

        despesaRepository.deleteById(despesa.getId());
        return ResponseEntity.noContent().build();
    }

    // --- CONCILIAÇÃO BANCÁRIA (Comprovantes Banco do Brasil) ---

    @PostMapping(value = "/conciliacao/processar-lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConciliacaoService.ConciliacaoResultado> processarLote(
            @RequestParam("parcelaId") UUID parcelaId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        segurancaService.garantirEhGestor("Somente gestores podem conciliar comprovantes.");
        return ResponseEntity.ok(conciliacaoService.processarLote(parcela, arquivos));
    }

    @PostMapping("/conciliacao/vincular")
    public ResponseEntity<ComprovanteDTO> vincularComprovante(@RequestBody VincularComprovanteDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem vincular comprovantes.");
        Parcela parcela = validarParcelaAutenticada(dto.parcelaId());
        return ResponseEntity.ok(conciliacaoService.vincularManual(parcela, dto.comprovanteId(), dto.despesaId()));
    }

    @PostMapping(value = "/{despesaId}/anexar-comprovante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DespesaResponseDTO> anexarComprovante(
            @PathVariable UUID despesaId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        segurancaService.garantirEhGestor("Somente gestores podem anexar comprovantes.");
        Despesa despesa = buscarDespesa(despesaId);
        segurancaService.garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());
        return ResponseEntity.ok(paraDTO(conciliacaoService.anexarComprovanteManualmente(despesa, arquivo)));
    }

    @GetMapping("/{parcelaId}/comprovantes-pendentes")
    public ResponseEntity<List<ComprovanteDTO>> comprovantesPendentes(@PathVariable UUID parcelaId) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        return ResponseEntity.ok(conciliacaoService.listarPendentes(parcela.getId()));
    }

    @GetMapping("/gerr/parcela/{parcelaId}/prontas-para-envio")
    public ResponseEntity<List<GerrPrestacaoDTO>> prontasParaEnvio(@PathVariable UUID parcelaId) {
        Parcela parcela = validarParcelaAutenticada(parcelaId);
        List<GerrPrestacaoDTO> lista = despesaRepository.findByParcelaId(parcela.getId()).stream()
                .filter(d -> d.getStatus() == StatusDespesa.MATCH_REALIZADO)
                .map(this::paraGerr)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(lista);
    }

    private GerrPrestacaoDTO paraGerr(Despesa despesa) {
        String urlNota = urlDoAnexo(despesa.getId(), TipoDocumento.NOTA_FISCAL);
        String urlComprovante = urlDoAnexo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO);
        if (urlNota == null || urlComprovante == null) return null;

        String dataPagamento = comprovanteBbRepository.findByDespesaId(despesa.getId()).stream()
                .map(com.tailorkz.gestao_entidades.domain.model.ComprovanteBb::getDataPagamento)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(LocalDate::toString)
                .orElse(null);

        String favorecido = despesa.getNomeEmpresa() != null && !despesa.getNomeEmpresa().isBlank()
                ? despesa.getNomeEmpresa() : despesa.getEmitente();

        return new GerrPrestacaoDTO(
                despesa.getId(),
                favorecido,
                despesa.getNumeroDocumento(),
                despesa.getDocumentoFavorecido(),
                despesa.getDataEmissao() != null ? despesa.getDataEmissao().toString() : null,
                dataPagamento,
                despesa.getValor().toString(),
                urlNota,
                urlComprovante
        );
    }

    private String urlDoAnexo(UUID despesaId, TipoDocumento tipo) {
        var anexo = documentoAnexoRepository.findByDespesaIdAndTipo(despesaId, tipo).stream().findFirst().orElse(null);
        if (anexo == null || anexo.getUrlS3() == null) return null;
        String nome = Paths.get(anexo.getUrlS3()).getFileName().toString();
        return "/arquivos/" + nome;
    }

    private Parcela validarParcelaAutenticada(UUID parcelaId) {
        if (segurancaService.ehSuperAdmin()) {
            return despesaService.buscarParcela(parcelaId);
        }
        return despesaService.parcelaDoTenant(parcelaId, segurancaService.tenantDoLogado());
    }

    private Usuario validarUsuarioAutenticado(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        segurancaService.garantirProprioOuGestor(usuarioId, usuario.getTenant().getId(), "Você só pode registrar despesas em seu próprio nome.");
        return usuario;
    }

    private Despesa buscarDespesa(UUID despesaId) {
        return despesaRepository.findById(despesaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada."));
    }

    private void anexar(UUID despesaId, TipoDocumento tipo, MultipartFile arquivo) {
        if (arquivo != null && !arquivo.isEmpty()) {
            anexoService.anexarArquivo(despesaId, tipo, arquivo);
        }
    }

    private DespesaResponseDTO paraDTO(Despesa despesa) {
        return new DespesaResponseDTO(
                despesa.getId(),
                despesa.getValor(),
                despesa.getDataCompetencia().toString(),
                despesa.getStatus().name(),
                despesa.getUsuario() != null ? despesa.getUsuario().getNome() : null,
                despesa.getNomeEmpresa(),
                despesa.getObservacao(),
                despesa.getEmitente(),
                despesa.getDocumentoFavorecido(),
                documentoAnexoRepository.existsByDespesaIdAndTipo(despesa.getId(), TipoDocumento.NOTA_FISCAL),
                documentoAnexoRepository.existsByDespesaIdAndTipo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO)
        );
    }

    private String limparDocumento(String documento) {
        if (documento == null || documento.isBlank()) return null;
        String limpo = documento.replaceAll("[^0-9]", "");
        return limpo.isEmpty() ? null : limpo;
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
record VincularComprovanteDTO(UUID parcelaId, UUID comprovanteId, UUID despesaId) {}
record EditarDespesaDTO(
        BigDecimal valor,
        String dataCompetencia,
        String emitente,
        String numero,
        String descricao,
        String nomeEmpresa,
        String observacao,
        String documentoFavorecido
) {}