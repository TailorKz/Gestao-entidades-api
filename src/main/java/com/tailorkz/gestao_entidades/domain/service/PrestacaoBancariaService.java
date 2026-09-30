package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.controller.dto.GrupoPrestacaoDTO;
import com.tailorkz.gestao_entidades.controller.dto.MesValoresDTO;
import com.tailorkz.gestao_entidades.controller.dto.RelatorioPrestacaoDTO;
import com.tailorkz.gestao_entidades.controller.dto.ResumoGrupoPrestacaoDTO;
import com.tailorkz.gestao_entidades.domain.enums.TipoMovimentoBancario;
import com.tailorkz.gestao_entidades.domain.model.ContaBancaria;
import com.tailorkz.gestao_entidades.domain.model.GrupoPrestacao;
import com.tailorkz.gestao_entidades.domain.model.TransacaoBancaria;
import com.tailorkz.gestao_entidades.domain.repository.ContaBancariaRepository;
import com.tailorkz.gestao_entidades.domain.repository.GrupoPrestacaoRepository;
import com.tailorkz.gestao_entidades.domain.repository.TransacaoBancariaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PrestacaoBancariaService {

    private static final String[] PALETA_CORES = {
            "#F59E0B", "#10B981", "#3B82F6", "#EF4444", "#8B5CF6",
            "#14B8A6", "#F97316", "#6366F1", "#EC4899", "#84CC16"
    };

    private final ContaBancariaRepository contaRepository;
    private final GrupoPrestacaoRepository grupoRepository;
    private final TransacaoBancariaRepository transacaoRepository;

    public PrestacaoBancariaService(ContaBancariaRepository contaRepository,
                                    GrupoPrestacaoRepository grupoRepository,
                                    TransacaoBancariaRepository transacaoRepository) {
        this.contaRepository = contaRepository;
        this.grupoRepository = grupoRepository;
        this.transacaoRepository = transacaoRepository;
    }

    // ==============================
    // CONTAS
    // ==============================

    @Transactional(readOnly = true)
    public List<ContaBancaria> listarContas() {
        return contaRepository.findAllByOrderByOrdemAsc();
    }

    @Transactional
    public ContaBancaria criarConta(String banco, String finalidade) {
        String nome = requerido(banco, "Informe o banco.");
        String finalidadeNorm = requerido(finalidade, "Informe a finalidade (ex: Esporte, Cultura).");
        int ordem = contaRepository.findAllByOrderByOrdemAsc().size() + 1;
        ContaBancaria conta = ContaBancaria.builder()
                .banco(nome.trim())
                .finalidade(finalidadeNorm.trim())
                .ordem(ordem)
                .build();
        return contaRepository.save(conta);
    }

    @Transactional
    public ContaBancaria editarConta(UUID id, String banco, String finalidade) {
        ContaBancaria conta = buscarConta(id);
        if (banco != null && !banco.isBlank()) conta.setBanco(banco.trim());
        if (finalidade != null && !finalidade.isBlank()) conta.setFinalidade(finalidade.trim());
        return contaRepository.save(conta);
    }

    @Transactional
    public void excluirConta(UUID id) {
        ContaBancaria conta = buscarConta(id);
        transacaoRepository.deleteByConta_Id(conta.getId());
        contaRepository.delete(conta);
    }

    private ContaBancaria buscarConta(UUID id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta bancária não encontrada."));
    }

    private String requerido(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        return valor;
    }

    // ==============================
    // TRANSAÇÕES / IMPORT OFX
    // ==============================

    // Importa um extrato OFX (parse linha a linha, ISO-8859-1): extrai data
    // (<DTPOSTED>, yyyyMMdd), valor (<TRNAMT>, sinal define Entrada/Saída) e
    // descrição (<MEMO>) de cada bloco <STMTTRN>. Mesmo fluxo do sistema antigo.
    @Transactional
    public int importarOfx(UUID contaId, MultipartFile arquivo) {
        ContaBancaria conta = buscarConta(contaId);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo OFX.");
        }

        List<TransacaoBancaria> lote = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.ISO_8859_1))) {
            String linha;
            TransacaoBancaria atual = null;

            while ((linha = br.readLine()) != null) {
                linha = linha.trim();

                if (linha.startsWith("<STMTTRN>")) {
                    atual = TransacaoBancaria.builder().conta(conta).build();
                } else if (linha.startsWith("<DTPOSTED>") && atual != null) {
                    String valor = semTag(linha);
                    if (valor.length() >= 8) {
                        atual.setData(LocalDate.parse(valor.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE));
                    }
                } else if (linha.startsWith("<TRNAMT>") && atual != null) {
                    BigDecimal valor = new BigDecimal(semTag(linha));
                    if (valor.signum() < 0) {
                        atual.setTipo(TipoMovimentoBancario.SAIDA);
                        atual.setValor(valor.abs());
                    } else {
                        atual.setTipo(TipoMovimentoBancario.ENTRADA);
                        atual.setValor(valor);
                    }
                } else if (linha.startsWith("<MEMO>") && atual != null) {
                    atual.setDescricao(semTag(linha));
                } else if (linha.startsWith("</STMTTRN>") && atual != null) {
                    if (atual.getData() != null && atual.getTipo() != null) {
                        lote.add(atual);
                    }
                    atual = null;
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falha ao processar o OFX: " + mensagemRaiz(e));
        }

        transacaoRepository.saveAll(lote);
        return lote.size();
    }

    // Retorna do dia 01/01 do ano até o fim do mês selecionado (mes=0 → ano todo).
    @Transactional(readOnly = true)
    public List<TransacaoBancaria> listarTransacoes(UUID contaId, int mes, int ano) {
        LocalDate inicio = LocalDate.of(ano, 1, 1);
        LocalDate fim = mes == 0
                ? LocalDate.of(ano, 12, 31)
                : YearMonth.of(ano, mes).atEndOfMonth();
        return transacaoRepository.findByConta_IdAndDataBetweenOrderByDataAsc(contaId, inicio, fim);
    }

    @Transactional
    public TransacaoBancaria adicionarManual(UUID contaId, LocalDate data, String descricao,
                                             TipoMovimentoBancario tipo, BigDecimal valor) {
        ContaBancaria conta = buscarConta(contaId);
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data.");
        }
        String nome = requerido(descricao, "Informe a descrição.");
        if (valor == null || valor.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um valor maior que zero.");
        }
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o tipo (ENTRADA ou SAIDA).");
        }
        TransacaoBancaria transacao = TransacaoBancaria.builder()
                .conta(conta)
                .data(data)
                .descricao(nome.trim())
                .tipo(tipo)
                .valor(valor)
                .build();
        return transacaoRepository.save(transacao);
    }

    @Transactional
    public void excluirTransacao(UUID id) {
        if (!transacaoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transação não encontrada.");
        }
        transacaoRepository.deleteById(id);
    }

    @Transactional
    public void limparMes(UUID contaId, int mes, int ano) {
        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = YearMonth.of(ano, mes).atEndOfMonth();
        transacaoRepository.deleteByConta_IdAndDataBetween(contaId, inicio, fim);
    }

    // ==============================
    // GRUPOS DE PRESTAÇÃO (por conta bancária)
    // ==============================

    @Transactional(readOnly = true)
    public List<GrupoPrestacao> listarGrupos(UUID contaId) {
        return grupoRepository.findByConta_IdOrderByOrdemAsc(contaId);
    }

    @Transactional
    public GrupoPrestacao criarGrupo(UUID contaId, String nome, String cor) {
        ContaBancaria conta = buscarConta(contaId);
        String nomeNorm = requerido(nome, "Informe o nome do grupo.");
        int ordem = grupoRepository.findByConta_IdOrderByOrdemAsc(contaId).size() + 1;
        GrupoPrestacao grupo = GrupoPrestacao.builder()
                .conta(conta)
                .nome(nomeNorm.trim())
                .cor(normalizarCor(cor, ordem - 1))
                .ordem(ordem)
                .build();
        return grupoRepository.save(grupo);
    }

    @Transactional
    public GrupoPrestacao editarGrupo(UUID contaId, UUID grupoId, String nome, String cor) {
        GrupoPrestacao grupo = buscarGrupo(contaId, grupoId);
        if (nome != null && !nome.isBlank()) grupo.setNome(nome.trim());
        if (cor != null && !cor.isBlank()) grupo.setCor(cor.trim());
        return grupoRepository.save(grupo);
    }

    // Exclui o grupo e deixa seus lançamentos "sem grupo" (a FK é anulada).
    @Transactional
    public void excluirGrupo(UUID contaId, UUID grupoId) {
        GrupoPrestacao grupo = buscarGrupo(contaId, grupoId);
        transacaoRepository.desvincularDoGrupo(grupo.getId());
        grupoRepository.delete(grupo);
    }

    private GrupoPrestacao buscarGrupo(UUID contaId, UUID grupoId) {
        GrupoPrestacao grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado."));
        if (!grupo.getConta().getId().equals(contaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O grupo pertence a outra conta bancária.");
        }
        return grupo;
    }

    private String normalizarCor(String cor, int indice) {
        if (cor == null || cor.isBlank()) {
            return PALETA_CORES[indice % PALETA_CORES.length];
        }
        String c = cor.trim();
        if (!c.startsWith("#")) c = "#" + c;
        return c.length() > 20 ? c.substring(0, 20) : c;
    }

    // ==============================
    // CLASSIFICAÇÃO EM LOTE
    // ==============================

    // Uma única requisição classifica/desclassifica vários lançamentos de uma vez.
    @Transactional
    public int classificar(List<UUID> ids, UUID grupoId) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos um lançamento.");
        }
        List<TransacaoBancaria> transacoes = transacaoRepository.findAllById(ids);
        if (transacoes.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um ou mais lançamentos não foram encontrados.");
        }

        GrupoPrestacao grupo = null;
        if (grupoId != null) {
            grupo = grupoRepository.findById(grupoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado."));
            UUID contaDoGrupo = grupo.getConta().getId();
            for (TransacaoBancaria t : transacoes) {
                if (!t.getConta().getId().equals(contaDoGrupo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O grupo pertence a outra conta bancária.");
                }
            }
        }

        for (TransacaoBancaria t : transacoes) {
            t.setGrupo(grupo);
        }
        transacaoRepository.saveAll(transacoes);
        return transacoes.size();
    }

    // ==============================
    // RELATÓRIO ANUAL (por grupo e por mês)
    // ==============================

    @Transactional(readOnly = true)
    public RelatorioPrestacaoDTO relatorioAno(UUID contaId, int ano) {
        List<TransacaoBancaria> transacoes = listarTransacoes(contaId, 0, ano);
        List<GrupoPrestacao> grupos = listarGrupos(contaId);

        Map<UUID, ResumoGrupo> porId = new LinkedHashMap<>();
        for (GrupoPrestacao g : grupos) {
            porId.put(g.getId(), new ResumoGrupo());
        }
        ResumoGrupo semGrupo = new ResumoGrupo();

        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSaidas = BigDecimal.ZERO;

        for (TransacaoBancaria t : transacoes) {
            ResumoGrupo alvo = t.getGrupo() == null ? semGrupo : porId.get(t.getGrupo().getId());
            if (alvo == null) alvo = semGrupo;
            BigDecimal valor = t.getValor();
            int mes = t.getData().getMonthValue() - 1;
            if (t.getTipo() == TipoMovimentoBancario.ENTRADA) {
                alvo.entradas = alvo.entradas.add(valor);
                alvo.entradasMes[mes] = alvo.entradasMes[mes].add(valor);
                totalEntradas = totalEntradas.add(valor);
            } else {
                alvo.saidas = alvo.saidas.add(valor);
                alvo.saidasMes[mes] = alvo.saidasMes[mes].add(valor);
                totalSaidas = totalSaidas.add(valor);
            }
            alvo.quantidade++;
        }

        List<ResumoGrupoPrestacaoDTO> resumos = new ArrayList<>();
        for (GrupoPrestacao g : grupos) {
            ResumoGrupo r = porId.get(g.getId());
            if (r.quantidade > 0) resumos.add(toResumoDTO(g, r));
        }
        if (semGrupo.quantidade > 0) resumos.add(toResumoDTO(null, semGrupo));

        long totalLancamentos = transacoes.size();
        return new RelatorioPrestacaoDTO(
                totalEntradas,
                totalSaidas,
                totalEntradas.subtract(totalSaidas),
                totalLancamentos,
                resumos
        );
    }

    private ResumoGrupoPrestacaoDTO toResumoDTO(GrupoPrestacao grupo, ResumoGrupo r) {
        GrupoPrestacaoDTO grupoDTO = grupo == null
                ? null
                : new GrupoPrestacaoDTO(grupo.getId(), grupo.getNome(), grupo.getCor(), grupo.getOrdem());
        List<MesValoresDTO> porMes = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            porMes.add(new MesValoresDTO(i + 1, r.entradasMes[i], r.saidasMes[i]));
        }
        return new ResumoGrupoPrestacaoDTO(
                grupoDTO,
                r.entradas,
                r.saidas,
                r.entradas.subtract(r.saidas),
                r.quantidade,
                porMes
        );
    }

    private static class ResumoGrupo {
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        final BigDecimal[] entradasMes = novoVetor();
        final BigDecimal[] saidasMes = novoVetor();
        long quantidade;

        private static BigDecimal[] novoVetor() {
            BigDecimal[] v = new BigDecimal[12];
            java.util.Arrays.fill(v, BigDecimal.ZERO);
            return v;
        }
    }

    private String semTag(String linha) {
        return linha.replaceAll("<[^>]+>", "").trim();
    }

    private String mensagemRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        return raiz.getMessage() != null ? raiz.getMessage() : raiz.getClass().getSimpleName();
    }
}