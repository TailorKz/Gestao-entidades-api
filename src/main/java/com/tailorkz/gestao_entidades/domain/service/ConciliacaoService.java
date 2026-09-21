package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.controller.dto.ComprovanteDTO;
import com.tailorkz.gestao_entidades.controller.dto.DadosComprovanteDTO;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.ComprovanteBbRepository;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.DocumentoAnexoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ConciliacaoService {

    private final OcrService ocrService;
    private final ComprovanteBbRepository comprovanteRepository;
    private final DespesaRepository despesaRepository;
    private final DocumentoAnexoRepository anexoRepository;
    private final DocumentoAnexoService anexoService;

    public ConciliacaoService(OcrService ocrService,
                              ComprovanteBbRepository comprovanteRepository,
                              DespesaRepository despesaRepository,
                              DocumentoAnexoRepository anexoRepository,
                              DocumentoAnexoService anexoService) {
        this.ocrService = ocrService;
        this.comprovanteRepository = comprovanteRepository;
        this.despesaRepository = despesaRepository;
        this.anexoRepository = anexoRepository;
        this.anexoService = anexoService;
    }

    @Transactional
    public ConciliacaoResultado processarLote(Parcela parcela, List<MultipartFile> arquivos) {
        List<ComprovanteBb> importados = new ArrayList<>();
        int ignoradosDuplicados = 0;
        List<String> erros = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) continue;

            String nome = arquivo.getOriginalFilename();
            if (nome != null && nome.toLowerCase().endsWith(".zip")) {
                ImportacaoZip zip = importarZip(parcela, arquivo);
                importados.addAll(zip.lidos());
                ignoradosDuplicados += zip.duplicados();
                erros.addAll(zip.erros());
            } else {
                byte[] bytes;
                try {
                    bytes = arquivo.getBytes();
                } catch (java.io.IOException e) {
                    erros.add((nome == null ? "(sem nome)" : nome) + ": " + e.getMessage());
                    continue;
                }
                try {
                    ComprovanteBb comp = importarPdf(parcela, bytes, nome);
                    if (comp == null) ignoradosDuplicados++;
                    else importados.add(comp);
                } catch (Exception e) {
                    erros.add((nome == null ? "(sem nome)" : nome) + ": " + mensagemRaiz(e));
                }
            }
        }

        // --- AUTO-MATCH por valor + favorecido/documento ---
        List<Despesa> candidatas = despesaRepository.findByParcelaId(parcela.getId()).stream()
                .filter(d -> d.getStatus() == StatusDespesa.PRONTA_PARA_MATCH)
                .filter(d -> !temComprovante(d.getId()))
                .collect(Collectors.toList());

        List<ComprovanteBb> vinculados = new ArrayList<>();
        List<ComprovanteBb> pendentes = new ArrayList<>();

        for (ComprovanteBb comp : importados) {
            List<Despesa> porValor = candidatas.stream()
                    .filter(d -> d.getValor().compareTo(comp.getValor()) == 0)
                    .collect(Collectors.toList());

            List<Despesa> fortes = porValor.stream()
                    .filter(d -> matchForte(comp, d))
                    .collect(Collectors.toList());

            Despesa escolhida = fortes.size() == 1 ? fortes.get(0) : null;

            if (escolhida != null) {
                vincular(comp, escolhida);
                vinculados.add(comp);
            } else {
                pendentes.add(comp);
            }
        }

        return new ConciliacaoResultado(
                vinculados.stream().map(this::paraDTO).toList(),
                pendentes.stream().map(this::paraDTO).toList(),
                ignoradosDuplicados,
                erros
        );
    }

    private ImportacaoZip importarZip(Parcela parcela, MultipartFile zip) {
        List<ComprovanteBb> lidos = new ArrayList<>();
        int duplicados = 0;
        List<String> erros = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".pdf")) {
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = zis.read(chunk)) != -1) buffer.write(chunk, 0, n);
                byte[] conteudo = buffer.toByteArray();
                String nome = entry.getName().contains("/")
                        ? entry.getName().substring(entry.getName().lastIndexOf('/') + 1)
                        : entry.getName();

                try {
                    ComprovanteBb comp = importarPdf(parcela, conteudo, nome);
                    if (comp == null) duplicados++;
                    else lidos.add(comp);
                } catch (Exception e) {
                    erros.add(nome + ": " + mensagemRaiz(e));
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler o arquivo ZIP: " + e.getMessage());
        }
        return new ImportacaoZip(lidos, duplicados, erros);
    }

    private String mensagemRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        return raiz.getMessage() != null ? raiz.getMessage() : raiz.getClass().getSimpleName();
    }

    private ComprovanteBb importarPdf(Parcela parcela, byte[] conteudo, String nome) {
        if (conteudo.length == 0) return null;
        String hash = sha256(conteudo);
        if (comprovanteRepository.findByHashArquivo(hash).isPresent()) return null;

        DadosComprovanteDTO dados = ocrService.extrairDadosComprovante(conteudo, nome);

        ComprovanteBb novo = new ComprovanteBb();
        novo.setParcela(parcela);
        novo.setValor(parseValor(dados.valor()));
        novo.setDataPagamento(parseData(dados.data()));
        novo.setFavorecido(vazioParaNull(dados.favorecido()));
        novo.setDocumentoFavorecido(dados.documento().isEmpty() ? null : dados.documento());
        novo.setAutenticacao(vazioParaNull(dados.autenticacao()));
        novo.setNomeArquivo(nome);
        novo.setHashArquivo(hash);
        novo.setChaveS3(anexoService.enviarParaArmazenamento(conteudo, nome));
        novo.setVinculado(false);

        return comprovanteRepository.save(novo);
    }

    @Transactional
    public ComprovanteDTO vincularManual(Parcela parcela, UUID comprovanteId, UUID despesaId) {
        ComprovanteBb comp = comprovanteRepository.findById(comprovanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado."));

        if (comp.getDespesa() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este comprovante já está vinculado a uma despesa.");
        }

        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada."));
        if (temComprovante(despesa.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta despesa já possui comprovante vinculado.");
        }

        vincular(comp, despesa);
        return paraDTO(comp);
    }

    @Transactional
    public Despesa anexarComprovanteManualmente(Despesa despesa, MultipartFile arquivo) {
        anexoService.anexarArquivo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO, arquivo);
        if (temNotaFiscal(despesa.getId())) {
            despesa.setStatus(StatusDespesa.MATCH_REALIZADO);
            return despesaRepository.save(despesa);
        }
        return despesa;
    }

    private void vincular(ComprovanteBb comp, Despesa despesa) {
        if (comp.getChaveS3() != null && !comp.getChaveS3().isBlank()) {
            anexoService.anexarExistente(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO, comp.getChaveS3(), comp.getNomeArquivo());
        } else if (comp.getArquivoPdf() != null) {
            anexoService.anexarArquivo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO, comp.getArquivoPdf(), comp.getNomeArquivo());
        }
        comp.setParcela(despesa.getParcela());
        comp.setDespesa(despesa);
        comp.setVinculado(true);
        comprovanteRepository.save(comp);

        if (temNotaFiscal(despesa.getId())) {
            despesa.setStatus(StatusDespesa.MATCH_REALIZADO);
            despesaRepository.save(despesa);
        }
    }

    public List<ComprovanteDTO> listarPendentes(UUID parcelaId) {
        return comprovanteRepository.findByParcelaIdAndDespesaIsNull(parcelaId).stream()
                .map(this::paraDTO).toList();
    }

    public List<ComprovanteDTO> listarComprovantes(UUID parcelaId) {
        return comprovanteRepository.findByParcelaIdOrderByDataPagamentoDesc(parcelaId).stream()
                .map(this::paraDTO).toList();
    }

    public List<ComprovanteDTO> listarVinculados(UUID parcelaId) {
        return comprovanteRepository.findByParcelaId(parcelaId).stream()
                .filter(c -> c.getDespesa() != null)
                .map(this::paraDTO).toList();
    }

    // --- REGRAS DE CORRESPONDÊNCIA ---
    private boolean matchForte(ComprovanteBb comp, Despesa despesa) {
        boolean docIgual = comp.getDocumentoFavorecido() != null && !comp.getDocumentoFavorecido().isEmpty()
                && despesa.getDocumentoFavorecido() != null
                && comp.getDocumentoFavorecido().equals(despesa.getDocumentoFavorecido());
        if (docIgual) return true;

        String nomeComp = normalizarNome(comp.getFavorecido());
        String nomeDespesa = normalizarNome(despesa.getNomeEmpresa() != null && !despesa.getNomeEmpresa().isBlank()
                ? despesa.getNomeEmpresa() : despesa.getEmitente());
        return terTokensComuns(nomeComp, nomeDespesa);
    }

    private boolean terTokensComuns(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        Set<String> tokensA = Set.of(a.split(" "));
        Set<String> tokensB = Set.of(b.split(" "));
        for (String token : tokensA) {
            if (token.length() >= 4 && tokensB.contains(token)) return true;
        }
        return false;
    }

    private String normalizarNome(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        n = n.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\b(LTDA|LTDA\\.|ME|EPP|S\\/A|SA|EIRELI|TECNOLOGIA|SERVI[CÇ]OS|COM[RÉ]RCIO|COMERCIO|EDUCA[CÇ][AÃ]O|RELIGI[OÓ]SA|ASSOCIA[CÇ][AÃ]O|FUNDA[CÇ][AÃ]O)$", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    // --- CONVERSÕES ---
    private ComprovanteDTO paraDTO(ComprovanteBb c) {
        Despesa d = c.getDespesa();
        return new ComprovanteDTO(
                c.getId(),
                c.getNomeArquivo(),
                c.getValor(),
                c.getDataPagamento() != null ? c.getDataPagamento().toString() : null,
                c.getFavorecido(),
                c.getDocumentoFavorecido(),
                c.getAutenticacao(),
                d != null ? d.getId() : null,
                d != null ? (d.getNomeEmpresa() != null && !d.getNomeEmpresa().isBlank() ? d.getNomeEmpresa() : d.getEmitente()) : null,
                d != null && temNotaFiscal(d.getId())
        );
    }

    private BigDecimal parseValor(String valorBr) {
        if (valorBr == null || valorBr.isBlank()) return BigDecimal.ZERO;
        String limpo = valorBr.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseData(String dataBr) {
        if (dataBr == null || dataBr.isBlank()) return null;
        try {
            return LocalDate.parse(dataBr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    private String vazioParaNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private boolean temNotaFiscal(UUID despesaId) {
        return anexoRepository.existsByDespesaIdAndTipo(despesaId, TipoDocumento.NOTA_FISCAL);
    }

    private boolean temComprovante(UUID despesaId) {
        return anexoRepository.existsByDespesaIdAndTipo(despesaId, TipoDocumento.COMPROVANTE_PAGAMENTO);
    }

    private String sha256(byte[] dados) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(dados);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao calcular hash do arquivo", e);
        }
    }

    public record ConciliacaoResultado(List<ComprovanteDTO> vinculados, List<ComprovanteDTO> pendentes, int ignoradosDuplicados, List<String> erros) {}

    private record ImportacaoZip(List<ComprovanteBb> lidos, int duplicados, List<String> erros) {}
}