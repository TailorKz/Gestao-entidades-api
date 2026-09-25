package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.controller.dto.ComprovanteDTO;
import com.tailorkz.gestao_entidades.controller.dto.DadosComprovanteDTO;
import com.tailorkz.gestao_entidades.controller.dto.MesComprovantesDTO;
import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    // ==============================
    // IMPORTAÇÃO POR MÊS (modelo novo)
    // ==============================

    // Importa um lote de comprovantes para o "balde" de um mês do setor.
    // Comprovantes nascem SEM parcela; o auto-match cruza com TODAS as despesas
    // do setor (qualquer parcela) que ainda não têm comprovante, exigindo valor
    // exato + correspondência forte (CPF/CNPJ ou primeiro nome) e a nota "esperada"
    // para o débito (emissão próxima ou competência do mês/anterior ao débito).
    @Transactional
    public ConciliacaoResultado importarMes(Tenant tenant, Categoria categoria, int ano, int mes,
                                            List<MultipartFile> arquivos) {
        LocalDate referencia = LocalDate.of(ano, mes, 1);

        List<ComprovanteBb> importados = new ArrayList<>();
        int ignoradosDuplicados = 0;
        List<String> erros = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) continue;
            String nome = arquivo.getOriginalFilename();
            if (nome != null && nome.toLowerCase().endsWith(".zip")) {
                ImportacaoZip zip = importarZip(tenant, categoria, referencia, arquivo);
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
                    ComprovanteBb comp = importarPdf(tenant, categoria, referencia, bytes, nome);
                    if (comp == null) ignoradosDuplicados++;
                    else importados.add(comp);
                } catch (Exception e) {
                    erros.add((nome == null ? "(sem nome)" : nome) + ": " + mensagemRaiz(e));
                }
            }
        }

        SetorDespesas setor = despesasDoSetorAnalisadas(tenant.getId(), categoria);
        List<Despesa> candidatasSetor = setor.semComprovante();

        List<ComprovanteBb> vinculados = new ArrayList<>();
        List<ComprovanteBb> pendentes = new ArrayList<>();
        Set<UUID> ouVincular = new java.util.HashSet<>();

        for (ComprovanteBb comp : importados) {
            List<Despesa> disponiveis = candidatasSetor.stream()
                    .filter(d -> !ouVincular.contains(d.getId()))
                    .filter(d -> esperaMatch(d, comp))
                    .collect(Collectors.toList());
            Despesa escolhida = escolherUnica(disponiveis, comp);
            if (escolhida != null) {
                ouVincular.add(escolhida.getId());
                vincular(comp, escolhida,
                        temTipo(setor.tipos(), escolhida.getId(), TipoDocumento.NOTA_FISCAL));
                vinculados.add(comp);
            } else {
                pendentes.add(comp);
            }
        }

        return new ConciliacaoResultado(
                paraDTOList(vinculados, setor.tipos()),
                paraDTOList(pendentes, setor.tipos()),
                ignoradosDuplicados,
                erros
        );
    }

    // Candidata aceita se: valor exato + correspondência forte de nome/doc
    // + a nota é "esperada" para este débito:
    //   - data de emissão: o débito ocorre de 0 a 45 dias DEPOIS da nota; ou
    //   - competência: no mês do débito ou no anterior.
    private boolean esperaMatch(Despesa despesa, ComprovanteBb comp) {
        if (despesa.getValor().compareTo(comp.getValor()) != 0) return false;
        if (!matchForte(comp, despesa)) return false;

        LocalDate debito = comp.getDataPagamento();
        if (debito != null && despesa.getDataEmissao() != null) {
            long dias = ChronoUnit.DAYS.between(despesa.getDataEmissao(), debito);
            if (dias >= 0 && dias <= 45) return true;
        }
        return dentroJanela(despesa, debito);
    }

    // Nota de competência X é paga no fim de X ou no início de X+1
    // (ex.: débito em 01/07 casa com nota de competência junho ou julho).
    private boolean dentroJanela(Despesa despesa, LocalDate debito) {
        if (debito == null) return false;
        YearMonth mesDebito = YearMonth.from(debito);
        YearMonth competencia = despesa.getDataCompetencia();
        return competencia != null
                && (competencia.equals(mesDebito) || competencia.equals(mesDebito.minusMonths(1)));
    }

    private Despesa escolherUnica(List<Despesa> candidatas, ComprovanteBb comp) {
        List<Despesa> fortes = candidatas.stream()
                .filter(d -> d.getValor().compareTo(comp.getValor()) == 0)
                .filter(d -> matchForte(comp, d))
                .collect(Collectors.toList());
        return fortes.size() == 1 ? fortes.get(0) : null;
    }

    private ImportacaoZip importarZip(Tenant tenant, Categoria categoria, LocalDate referencia, MultipartFile zip) {
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
                    ComprovanteBb comp = importarPdf(tenant, categoria, referencia, conteudo, nome);
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

    private ComprovanteBb importarPdf(Tenant tenant, Categoria categoria, LocalDate referencia,
                                      byte[] conteudo, String nome) {
        if (conteudo.length == 0) return null;
        String hash = sha256(conteudo);
        if (comprovanteRepository.findByHashArquivo(hash).isPresent()) return null;

        DadosComprovanteDTO dados = ocrService.extrairDadosComprovante(conteudo, nome);

        ComprovanteBb novo = new ComprovanteBb();
        novo.setTenant(tenant);
        novo.setCategoria(categoria);
        novo.setDataReferencia(referencia);
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

    // ==============================
    // CONSULTAS POR MÊS / SETOR
    // ==============================

    // Despesas do setor que ainda aceitam comprovante (dropdown de vínculo manual).
    // Os anexos de TODA a consulta saem em UMA query agregada (nada de 1 query por despesa).
    public List<Despesa> candidatasDoSetor(UUID tenantId, Categoria categoria) {
        return despesasDoSetorAnalisadas(tenantId, categoria).semComprovante();
    }

    private SetorDespesas despesasDoSetorAnalisadas(UUID tenantId, Categoria categoria) {
        List<Despesa> despesas = despesaRepository
                .findByParcela_Fomento_TenantIdAndParcela_Fomento_Categoria(tenantId, categoria);
        if (despesas.isEmpty()) return new SetorDespesas(List.of(), Map.of());
        Map<UUID, Set<TipoDocumento>> tipos = tiposPorDespesa(
                despesas.stream().map(Despesa::getId).collect(Collectors.toSet()));
        List<Despesa> semComprovante = despesas.stream()
                .filter(d -> !temTipo(tipos, d.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO))
                .toList();
        return new SetorDespesas(semComprovante, tipos);
    }

    public List<ComprovanteDTO> listarComprovantesDoMes(UUID tenantId, Categoria categoria, int ano, int mes) {
        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.plusMonths(1).minusDays(1);
        return paraDTOList(comprovanteRepository
                .findByTenant_IdAndCategoriaAndDataReferenciaBetweenOrderByDataPagamentoDesc(
                        tenantId, categoria, inicio, fim));
    }

    public List<MesComprovantesDTO> sumarizar(UUID tenantId, Categoria categoria, int ano) {
        LocalDate inicio = LocalDate.of(ano, 1, 1);
        LocalDate fim = LocalDate.of(ano, 12, 31);
        List<ComprovanteBb> todos = comprovanteRepository
                .findByTenant_IdAndCategoriaAndDataReferenciaBetweenOrderByDataPagamentoDesc(
                        tenantId, categoria, inicio, fim);

        Map<Integer, ContagemMes> porMes = new TreeMap<>();
        for (ComprovanteBb c : todos) {
            int m = c.getDataReferencia() != null ? c.getDataReferencia().getMonthValue() : -1;
            if (m < 1) continue;
            ContagemMes cont = porMes.computeIfAbsent(m, ContagemMes::new);
            cont.total++;
            if (c.getDespesa() != null) cont.vinculados++;
            else cont.pendentes++;
        }

        return IntStream.rangeClosed(1, 12).mapToObj(m -> {
            ContagemMes cont = porMes.getOrDefault(m, new ContagemMes());
            return new MesComprovantesDTO(m, cont.total, cont.vinculados, cont.pendentes);
        }).toList();
    }

    // ==============================
    // VÍNCULO
    // ==============================

    // Vínculo manual por um comprovante importado (sem parcela obrigatória).
    @Transactional
    public ComprovanteDTO vincularManual(UUID tenantId, Categoria categoria, UUID comprovanteId, UUID despesaId) {
        ComprovanteBb comp = comprovanteRepository.findById(comprovanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado."));

        if (comp.getDespesa() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este comprovante já está vinculado a uma despesa.");
        }
        if (comp.getTenant() != null && !comp.getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comprovante de outra entidade.");
        }
        if (comp.getCategoria() != null && categoria != null && comp.getCategoria() != categoria) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O comprovante pertence a outro setor.");
        }

        Despesa despesa = despesaRepository.findById(despesaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada."));
        if (!despesa.getParcela().getFomento().getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Despesa de outra entidade.");
        }
        Categoria setor = categoria != null ? categoria : comp.getCategoria();
        if (setor != null && despesa.getParcela().getFomento().getCategoria() != setor) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A despesa não pertence ao setor selecionado.");
        }
        if (temComprovante(despesa.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta despesa já possui comprovante vinculado.");
        }

        vincular(comp, despesa);
        return paraDTO(comp);
    }

    private void vincular(ComprovanteBb comp, Despesa despesa) {
        vincular(comp, despesa, temNotaFiscal(despesa.getId()));
    }

    private void vincular(ComprovanteBb comp, Despesa despesa, boolean temNota) {
        if (comp.getChaveS3() != null && !comp.getChaveS3().isBlank()) {
            anexoService.anexarExistente(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO, comp.getChaveS3(), comp.getNomeArquivo());
        } else if (comp.getArquivoPdf() != null) {
            anexoService.anexarArquivo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO, comp.getArquivoPdf(), comp.getNomeArquivo());
        }
        comp.setParcela(despesa.getParcela());
        comp.setDespesa(despesa);
        comp.setVinculado(true);
        comprovanteRepository.save(comp);

        if (temNota) {
            despesa.setStatus(StatusDespesa.MATCH_REALIZADO);
            despesaRepository.save(despesa);
        }
    }

    // Desfaz o vínculo mantendo o comprovante salvo (aguardando novo vínculo).
    // O arquivo não é apagado do armazenamento — apenas o anexo da despesa é removido.
    @Transactional
    public ComprovanteDTO desvincular(UUID tenantId, UUID comprovanteId) {
        ComprovanteBb comp = comprovanteRepository.findById(comprovanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado."));
        Despesa despesa = comp.getDespesa();
        if (despesa == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este comprovante não está vinculado a nenhuma despesa.");
        }
        if (!despesa.getParcela().getFomento().getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comprovante de outra entidade.");
        }

        List<DocumentoAnexo> anexosDespesa = anexoRepository.findByDespesaIdAndTipo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO);
        anexoRepository.deleteAll(anexosDespesa);

        comp.setDespesa(null);
        comp.setParcela(null);
        comp.setVinculado(false);
        comprovanteRepository.save(comp);

        despesa.setStatus(temNotaFiscal(despesa.getId())
                ? StatusDespesa.PRONTA_PARA_MATCH
                : StatusDespesa.AGUARDANDO_DOCUMENTOS);
        despesaRepository.save(despesa);
        return paraDTO(comp);
    }

    // ==============================
    // FLUXO LEGADO (por parcela) — mantido por compatibilidade
    // ==============================

    @Transactional
    public ConciliacaoResultado processarLote(Parcela parcela, List<MultipartFile> arquivos) {
        Tenant tenant = parcela.getFomento().getTenant();
        Categoria categoria = parcela.getFomento().getCategoria();

        List<ComprovanteBb> importados = new ArrayList<>();
        int ignoradosDuplicados = 0;
        List<String> erros = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) continue;
            String nome = arquivo.getOriginalFilename();
            if (nome != null && nome.toLowerCase().endsWith(".zip")) {
                ImportacaoZip zip = importarZipLegado(parcela, tenant, categoria, arquivo);
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
                    ComprovanteBb comp = importarPdfLegado(parcela, tenant, categoria, bytes, nome);
                    if (comp == null) ignoradosDuplicados++;
                    else importados.add(comp);
                } catch (Exception e) {
                    erros.add((nome == null ? "(sem nome)" : nome) + ": " + mensagemRaiz(e));
                }
            }
        }

        List<Despesa> despesasDaParcela = despesaRepository.findByParcelaId(parcela.getId());
        Map<UUID, Set<TipoDocumento>> tiposDaParcela = tiposPorDespesa(
                despesasDaParcela.stream().map(Despesa::getId).collect(Collectors.toSet()));
        List<Despesa> candidatas = despesasDaParcela.stream()
                .filter(d -> d.getStatus() == StatusDespesa.PRONTA_PARA_MATCH)
                .filter(d -> !temTipo(tiposDaParcela, d.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO))
                .collect(Collectors.toList());

        List<ComprovanteBb> vinculados = new ArrayList<>();
        List<ComprovanteBb> pendentes = new ArrayList<>();
        Set<UUID> ouVincular = new java.util.HashSet<>();

        for (ComprovanteBb comp : importados) {
            List<Despesa> disponiveis = candidatas.stream()
                    .filter(d -> !ouVincular.contains(d.getId()))
                    .collect(Collectors.toList());
            Despesa escolhida = escolherUnica(disponiveis, comp);
            if (escolhida != null) {
                ouVincular.add(escolhida.getId());
                vincular(comp, escolhida,
                        temTipo(tiposDaParcela, escolhida.getId(), TipoDocumento.NOTA_FISCAL));
                vinculados.add(comp);
            } else {
                pendentes.add(comp);
            }
        }

        return new ConciliacaoResultado(
                paraDTOList(vinculados, tiposDaParcela),
                paraDTOList(pendentes, tiposDaParcela),
                ignoradosDuplicados,
                erros
        );
    }

    private ImportacaoZip importarZipLegado(Parcela parcela, Tenant tenant, Categoria categoria, MultipartFile zip) {
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
                    ComprovanteBb comp = importarPdfLegado(parcela, tenant, categoria, conteudo, nome);
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

    private ComprovanteBb importarPdfLegado(Parcela parcela, Tenant tenant, Categoria categoria,
                                            byte[] conteudo, String nome) {
        ComprovanteBb comp = importarPdf(tenant, categoria, LocalDate.now().withDayOfMonth(1), conteudo, nome);
        if (comp != null) {
            comp.setParcela(parcela);
            comprovanteRepository.save(comp);
        }
        return comp;
    }

    @Transactional
    public ComprovanteDTO vincularManual(Parcela parcela, UUID comprovanteId, UUID despesaId) {
        return vincularManual(parcela.getFomento().getTenant().getId(),
                parcela.getFomento().getCategoria(), comprovanteId, despesaId);
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

    public List<ComprovanteDTO> listarPendentes(UUID parcelaId) {
        return paraDTOList(comprovanteRepository.findByParcelaIdAndDespesaIsNull(parcelaId));
    }

    public List<ComprovanteDTO> listarComprovantes(UUID parcelaId) {
        return paraDTOList(comprovanteRepository.findByParcelaIdOrderByDataPagamentoDesc(parcelaId));
    }

    public List<ComprovanteDTO> listarVinculados(UUID parcelaId) {
        return paraDTOList(comprovanteRepository.findByParcelaId(parcelaId).stream()
                .filter(c -> c.getDespesa() != null)
                .toList());
    }

    @Transactional
    public void excluirComprovanteDaParcela(UUID parcelaId, UUID comprovanteId) {
        ComprovanteBb comp = comprovanteRepository.findById(comprovanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado."));

        excluirInterno(comp);
    }

    @Transactional
    public void excluirComprovante(UUID tenantId, UUID comprovanteId) {
        ComprovanteBb comp = comprovanteRepository.findById(comprovanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprovante não encontrado."));
        Despesa despesa = comp.getDespesa();
        if (despesa != null) {
            if (!despesa.getParcela().getFomento().getTenant().getId().equals(tenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comprovante de outra entidade.");
            }
        } else if (comp.getTenant() != null && !comp.getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comprovante de outra entidade.");
        }
        excluirInterno(comp);
    }

    @Transactional
    public void excluirComprovantesDoMes(UUID tenantId, Categoria categoria, int ano, int mes) {
        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.plusMonths(1).minusDays(1);
        List<ComprovanteBb> doMes = comprovanteRepository
                .findByTenant_IdAndCategoriaAndDataReferenciaBetweenOrderByDataPagamentoDesc(tenantId, categoria, inicio, fim);

        Set<UUID> despesasVistas = new java.util.HashSet<>();
        Map<UUID, Set<TipoDocumento>> tiposDoMes = tiposPorDespesa(doMes.stream()
                .map(ComprovanteBb::getDespesa)
                .filter(Objects::nonNull)
                .map(Despesa::getId)
                .collect(Collectors.toSet()));
        for (ComprovanteBb comp : doMes) {
            if (comp.getDespesa() != null) {
                UUID despesaId = comp.getDespesa().getId();
                if (!despesasVistas.add(despesaId)) {
                    comprovanteRepository.delete(comp);
                    continue;
                }
            }
            excluirInterno(comp, tiposDoMes);
        }
    }

    private void excluirInterno(ComprovanteBb comp) {
        Despesa despesa = comp.getDespesa();
        excluirInterno(comp, despesa != null
                ? tiposPorDespesa(List.of(despesa.getId()))
                : Map.of());
    }

    private void excluirInterno(ComprovanteBb comp, Map<UUID, Set<TipoDocumento>> tipos) {
        Despesa despesa = comp.getDespesa();
        if (despesa != null) {
            List<DocumentoAnexo> anexosDespesa = anexoRepository.findByDespesaIdAndTipo(despesa.getId(), TipoDocumento.COMPROVANTE_PAGAMENTO);
            for (DocumentoAnexo a : anexosDespesa) {
                anexoService.deletarArquivo(a.getUrlS3());
                anexoRepository.delete(a);
            }
        } else if (comp.getChaveS3() != null && !comp.getChaveS3().isBlank()) {
            anexoService.deletarArquivo(comp.getChaveS3());
        }

        comprovanteRepository.delete(comp);

        if (despesa != null) {
            despesa.setStatus(temTipo(tipos, despesa.getId(), TipoDocumento.NOTA_FISCAL)
                    ? StatusDespesa.PRONTA_PARA_MATCH
                    : StatusDespesa.AGUARDANDO_DOCUMENTOS);
            despesaRepository.save(despesa);
        }
    }

    // --- REGRAS DE CORRESPONDÊNCIA ---
    private boolean matchForte(ComprovanteBb comp, Despesa despesa) {
        boolean docIgual = comp.getDocumentoFavorecido() != null && !comp.getDocumentoFavorecido().isEmpty()
                && despesa.getDocumentoFavorecido() != null
                && comp.getDocumentoFavorecido().equals(despesa.getDocumentoFavorecido());
        if (docIgual) return true;

        String nomeComp = normalizarNome(comp.getFavorecido());
        if (nomesBatem(nomeComp, normalizarNome(despesa.getNomeEmpresa()))) return true;
        if (nomesBatem(nomeComp, normalizarNome(despesa.getEmitente()))) return true;
        return false;
    }

    // Nenhum instrutor tem o primeiro nome igual a outro: bater só o primeiro
    // nome já é uma correspondência forte quando o valor também bate.
    private boolean nomesBatem(String nomeComp, String nomeDespesa) {
        if (nomeDespesa.isEmpty()) return false;
        return primeiroNomeIgual(nomeComp, nomeDespesa) || terTokensComuns(nomeComp, nomeDespesa);
    }

    private boolean primeiroNomeIgual(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        String primeiroA = a.split(" ")[0];
        String primeiroB = b.split(" ")[0];
        return primeiroA.equals(primeiroB) && primeiroA.length() >= 3;
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
        return paraDTO(c, d != null && temNotaFiscal(d.getId()));
    }

    private ComprovanteDTO paraDTO(ComprovanteBb c, Map<UUID, Set<TipoDocumento>> tipos) {
        Despesa d = c.getDespesa();
        boolean temNota = d != null && temTipo(tipos, d.getId(), TipoDocumento.NOTA_FISCAL);
        return paraDTO(c, temNota);
    }

    private ComprovanteDTO paraDTO(ComprovanteBb c, boolean temNota) {
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
                temNota,
                c.getChaveS3(),
                c.getDataReferencia() != null ? c.getDataReferencia().toString() : null,
                d != null && d.getParcela() != null ? d.getParcela().getNumero() : null,
                c.getCategoria() != null ? c.getCategoria().name() : null,
                d != null && d.getDataEmissao() != null ? d.getDataEmissao().toString() : null,
                d != null ? d.getValor() : null
        );
    }

    // Conversão em lote SEM N+1: os tipos de anexo de todos os comprovantes
    // saem em UMA única query (ou são reaproveitados do mapa já carregado).
    private List<ComprovanteDTO> paraDTOList(List<ComprovanteBb> comprovantes) {
        if (comprovantes.isEmpty()) return List.of();
        Set<UUID> despesaIds = comprovantes.stream()
                .map(ComprovanteBb::getDespesa)
                .filter(Objects::nonNull)
                .map(Despesa::getId)
                .collect(Collectors.toSet());
        return paraDTOList(comprovantes, tiposPorDespesa(despesaIds));
    }

    private List<ComprovanteDTO> paraDTOList(List<ComprovanteBb> comprovantes, Map<UUID, Set<TipoDocumento>> tipos) {
        return comprovantes.stream().map(c -> paraDTO(c, tipos)).toList();
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

    // Todos os tipos de anexo das despesas do lote em UMA única query agregada.
    private Map<UUID, Set<TipoDocumento>> tiposPorDespesa(Collection<UUID> despesaIds) {
        if (despesaIds == null || despesaIds.isEmpty()) return Map.of();
        Map<UUID, Set<TipoDocumento>> tipos = new HashMap<>();
        for (Object[] linha : anexoRepository.findTiposPorDespesas(despesaIds)) {
            tipos.computeIfAbsent((UUID) linha[0], k -> EnumSet.noneOf(TipoDocumento.class))
                    .add((TipoDocumento) linha[1]);
        }
        return tipos;
    }

    private boolean temTipo(Map<UUID, Set<TipoDocumento>> tipos, UUID despesaId, TipoDocumento tipo) {
        Set<TipoDocumento> presentes = tipos.get(despesaId);
        return presentes != null && presentes.contains(tipo);
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

    private String mensagemRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        return raiz.getMessage() != null ? raiz.getMessage() : raiz.getClass().getSimpleName();
    }

    public record ConciliacaoResultado(List<ComprovanteDTO> vinculados, List<ComprovanteDTO> pendentes, int ignoradosDuplicados, List<String> erros) {}

    private record SetorDespesas(List<Despesa> semComprovante, Map<UUID, Set<TipoDocumento>> tipos) {}

    private record ImportacaoZip(List<ComprovanteBb> lidos, int duplicados, List<String> erros) {}

    private static class ContagemMes {
        long total;
        long vinculados;
        long pendentes;

        ContagemMes() {
        }

        ContagemMes(@SuppressWarnings("unused") int mes) {
        }
    }
}