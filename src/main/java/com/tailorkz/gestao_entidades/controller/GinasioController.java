package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.DiaSemana;
import com.tailorkz.gestao_entidades.domain.enums.Ginasio;
import com.tailorkz.gestao_entidades.domain.enums.TipoAjuste;
import com.tailorkz.gestao_entidades.domain.model.AjusteGinasio;
import com.tailorkz.gestao_entidades.domain.model.ReservaGinasio;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.repository.AjusteGinasioRepository;
import com.tailorkz.gestao_entidades.domain.repository.ReservaGinasioRepository;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/ginasios")
@CrossOrigin(origins = "*")
public class GinasioController {

    public static final BigDecimal VALOR_DIA_PADRAO = new BigDecimal("25.00");

    private final ReservaGinasioRepository reservaRepository;
    private final AjusteGinasioRepository ajusteRepository;
    private final TenantRepository tenantRepository;
    private final SegurancaService segurancaService;

    public GinasioController(ReservaGinasioRepository reservaRepository,
                             AjusteGinasioRepository ajusteRepository,
                             TenantRepository tenantRepository,
                             SegurancaService segurancaService) {
        this.reservaRepository = reservaRepository;
        this.ajusteRepository = ajusteRepository;
        this.tenantRepository = tenantRepository;
        this.segurancaService = segurancaService;
    }

    // ============================ PAINEL ============================

    @GetMapping
    public ResponseEntity<PainelGinasiosDTO> painel(
            @RequestParam(value = "mesInicio", required = false) String mesInicio,
            @RequestParam(value = "mesFim", required = false) String mesFim) {
        UUID tenantId = segurancaService.tenantDoLogado();

        YearMonth inicio = parseMes(mesInicio, YearMonth.now());
        YearMonth fim = parseMes(mesFim, inicio.plusMonths(1));
        if (fim.isBefore(inicio)) {
            YearMonth aux = inicio;
            inicio = fim;
            fim = aux;
        }
        LocalDate dataInicio = inicio.atDay(1);
        LocalDate dataFim = fim.atEndOfMonth();

        List<ReservaGinasio> reservas = reservaRepository.findByTenantIdOrderByNomeAsc(tenantId);
        List<AjusteGinasio> ajustes = ajusteRepository.findByTenantIdAndDataBetween(tenantId, dataInicio, dataFim);

        Map<UUID, List<AjusteGinasio>> ajustesPorReserva = new HashMap<>();
        Map<Ginasio, List<AjusteGinasio>> geraisPorGinasio = new EnumMap<>(Ginasio.class);
        for (AjusteGinasio a : ajustes) {
            if (a.getReserva() == null) {
                geraisPorGinasio.computeIfAbsent(a.getGinasio(), k -> new ArrayList<>()).add(a);
            } else {
                ajustesPorReserva.computeIfAbsent(a.getReserva().getId(), k -> new ArrayList<>()).add(a);
            }
        }

        Map<Ginasio, Integer> qtdePessoasPorGinasio = new EnumMap<>(Ginasio.class);
        Map<Ginasio, Map<DiaSemana, List<ReservaGinasio>>> pessoasPorGinasioDia = new EnumMap<>(Ginasio.class);
        for (ReservaGinasio r : reservas) {
            qtdePessoasPorGinasio.merge(r.getGinasio(), 1, Integer::sum);
            pessoasPorGinasioDia
                    .computeIfAbsent(r.getGinasio(), k -> new EnumMap<>(DiaSemana.class))
                    .computeIfAbsent(r.getDiaSemana(), k -> new ArrayList<>())
                    .add(r);
        }

        int totalPessoas = 0;
        BigDecimal totalGeral = BigDecimal.ZERO;
        List<GinasioDTO> ginasios = new ArrayList<>();

        for (Ginasio ginasio : Ginasio.values()) {
            List<DiaGinasioDTO> dias = new ArrayList<>();
            BigDecimal totalGinasio = BigDecimal.ZERO;

            for (DiaSemana dia : DiaSemana.values()) {
                List<LocalDate> base = ocorrencias(inicio, fim, dia.getDayOfWeek());
                List<ReservaGinasio> pessoasDoDia = pessoasPorGinasioDia
                        .getOrDefault(ginasio, Collections.emptyMap())
                        .getOrDefault(dia, List.of());
                List<AjusteGinasio> gerais = geraisPorGinasio.getOrDefault(ginasio, List.of());

                List<PessoaGinasioDTO> pessoas = new ArrayList<>();
                BigDecimal subtotal = BigDecimal.ZERO;
                for (ReservaGinasio reserva : pessoasDoDia) {
                    List<AjusteGinasio> daPessoa = ajustesPorReserva.getOrDefault(reserva.getId(), List.of());

                    Set<LocalDate> cobrados = calcularDiasCobrados(base, daPessoa, gerais);

                    List<DataStatusDTO> datas = montarDatas(base, daPessoa, gerais);
                    List<AjusteDTO> ajustesDTO = daPessoa.stream()
                            .sorted(Comparator.comparing(AjusteGinasio::getData))
                            .map(a -> toAjusteDTO(a, false))
                            .toList();

                    BigDecimal valorDiaPessoa = reserva.getValorDia() != null ? reserva.getValorDia() : VALOR_DIA_PADRAO;
                    BigDecimal valor = valorDiaPessoa.multiply(BigDecimal.valueOf(cobrados.size()));
                    pessoas.add(new PessoaGinasioDTO(
                            reserva.getId(),
                            reserva.getNome(),
                            reserva.getGinasio().name(),
                            reserva.getDiaSemana().name(),
                            valorDiaPessoa,
                            cobrados.size(),
                            valor,
                            datas,
                            ajustesDTO));
                    subtotal = subtotal.add(valor);
                }

                List<AjusteDTO> geraisDTO = gerais.stream()
                        .filter(a -> a.getData().getDayOfWeek() == dia.getDayOfWeek())
                        .sorted(Comparator.comparing(AjusteGinasio::getData))
                        .map(a -> toAjusteDTO(a, true))
                        .toList();

                dias.add(new DiaGinasioDTO(
                        dia.name(),
                        dia.getRotulo(),
                        base.size(),
                        subtotal,
                        pessoas,
                        geraisDTO));
                totalGinasio = totalGinasio.add(subtotal);
            }

            ginasios.add(new GinasioDTO(ginasio.name(), ginasio.getRotulo(), totalGinasio, dias));
            totalPessoas += qtdePessoasPorGinasio.getOrDefault(ginasio, 0);
            totalGeral = totalGeral.add(totalGinasio);
        }

        String rotuloPeriodo;
        if (inicio.equals(fim)) {
            rotuloPeriodo = inicio.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("pt", "BR"))
                    + " de " + inicio.getYear();
        } else {
            rotuloPeriodo = inicio.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("pt", "BR"))
                    + " e " + fim.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("pt", "BR"))
                    + " de " + fim.getYear();
        }

        return ResponseEntity.ok(new PainelGinasiosDTO(
                new PeriodoDTO(inicio.toString(), fim.toString(), rotuloPeriodo),
                totalPessoas,
                totalGeral,
                ginasios));
    }

    private Set<LocalDate> calcularDiasCobrados(List<LocalDate> base,
                                                List<AjusteGinasio> daPessoa,
                                                List<AjusteGinasio> gerais) {
        Set<LocalDate> cobrados = new LinkedHashSet<>(base);
        for (AjusteGinasio a : daPessoa) {
            if (a.getTipo() == TipoAjuste.ADICIONADO) cobrados.add(a.getData());
        }
        for (AjusteGinasio a : gerais) {
            if (a.getTipo() == TipoAjuste.ADICIONADO) cobrados.add(a.getData());
        }
        for (AjusteGinasio a : daPessoa) {
            if (a.getTipo() == TipoAjuste.REMOVIDO) cobrados.remove(a.getData());
        }
        for (AjusteGinasio a : gerais) {
            if (a.getTipo() == TipoAjuste.REMOVIDO) cobrados.remove(a.getData());
        }
        return cobrados;
    }

    private List<DataStatusDTO> montarDatas(List<LocalDate> base,
                                            List<AjusteGinasio> daPessoa,
                                            List<AjusteGinasio> gerais) {
        Set<LocalDate> removidos = new HashSet<>();
        Set<LocalDate> adicionados = new HashSet<>();
        for (AjusteGinasio a : daPessoa) {
            (a.getTipo() == TipoAjuste.REMOVIDO ? removidos : adicionados).add(a.getData());
        }
        for (AjusteGinasio a : gerais) {
            (a.getTipo() == TipoAjuste.REMOVIDO ? removidos : adicionados).add(a.getData());
        }

        Set<LocalDate> titulos = new TreeSet<>(base);
        titulos.addAll(removidos);
        titulos.addAll(adicionados);

        List<DataStatusDTO> datas = new ArrayList<>();
        for (LocalDate d : titulos) {
            boolean emBase = base.contains(d);
            boolean removido = removidos.contains(d);
            boolean adicionado = adicionados.contains(d);
            String tipo = removido ? "REMOVIDO"
                    : (adicionado && !emBase ? "ADICIONADO" : "NORMAL");
            datas.add(new DataStatusDTO(d.toString(), tipo));
        }
        return datas;
    }

    private List<LocalDate> ocorrencias(YearMonth inicio, YearMonth fim, DayOfWeek dayOfWeek) {
        List<LocalDate> lista = new ArrayList<>();
        for (YearMonth ym : new YearMonth[]{inicio, fim}) {
            for (int dia = 1; dia <= ym.lengthOfMonth(); dia++) {
                LocalDate data = ym.atDay(dia);
                if (data.getDayOfWeek() == dayOfWeek) lista.add(data);
            }
        }
        return lista;
    }

    // ============================ PESSOAS ============================

    @PostMapping("/pessoas")
    public ResponseEntity<ReservaModalDTO> criarPessoa(@RequestBody CriarReservaDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar as reservas dos ginásios.");
        Tenant tenant = tenantAtual();
        String nome = obrigatorio(dto.nome(), "Informe o nome da pessoa.");
        Ginasio ginasio = parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido.");
        DiaSemana dia = parseEnum(DiaSemana.class, dto.diaSemana(), "Dia da semana inválido.");

        ReservaGinasio reserva = new ReservaGinasio();
        reserva.setTenant(tenant);
        reserva.setGinasio(ginasio);
        reserva.setDiaSemana(dia);
        reserva.setNome(nome);
        reserva.setValorDia(parseValorDia(dto.valorDia()));
        reserva = reservaRepository.save(reserva);

        return ResponseEntity.status(HttpStatus.CREATED).body(toReservaModalDTO(reserva));
    }

    @PutMapping("/pessoas/{id}")
    public ResponseEntity<ReservaModalDTO> atualizarPessoa(@PathVariable UUID id, @RequestBody AtualizarReservaDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar as reservas dos ginásios.");
        ReservaGinasio reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        segurancaService.garantirAcessoTenant(reserva.getTenant().getId());

        if (dto.nome() != null && !dto.nome().isBlank()) reserva.setNome(dto.nome().trim());
        if (dto.ginasio() != null && !dto.ginasio().isBlank()) {
            reserva.setGinasio(parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido."));
        }
        if (dto.diaSemana() != null && !dto.diaSemana().isBlank()) {
            reserva.setDiaSemana(parseEnum(DiaSemana.class, dto.diaSemana(), "Dia da semana inválido."));
        }
        if (dto.valorDia() != null) {
            reserva.setValorDia(parseValorDia(dto.valorDia()));
        }

        reserva = reservaRepository.save(reserva);
        return ResponseEntity.ok(toReservaModalDTO(reserva));
    }

    @DeleteMapping("/pessoas/{id}")
    public ResponseEntity<Void> excluirPessoa(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar as reservas dos ginásios.");
        ReservaGinasio reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        segurancaService.garantirAcessoTenant(reserva.getTenant().getId());

        ajusteRepository.deleteByReservaId(id);
        reservaRepository.delete(reserva);
        return ResponseEntity.noContent().build();
    }

    // ============================ AJUSTES ============================

    @PostMapping("/ajustes")
    public ResponseEntity<AjusteDTO> criarAjuste(@RequestBody CriarAjusteDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar os dias dos ginásios.");
        Tenant tenant = tenantAtual();
        LocalDate data = parseData(dto.data(), "Data inválida. Use o formato AAAA-MM-DD.");
        TipoAjuste tipo = parseEnum(TipoAjuste.class, dto.tipo(), "Tipo de ajuste inválido. Use REMOVIDO ou ADICIONADO.");

        Ginasio ginasio;
        ReservaGinasio reserva = null;
        if (dto.reservaId() != null) {
            reserva = reservaRepository.findById(dto.reservaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
            segurancaService.garantirAcessoTenant(reserva.getTenant().getId());
            ginasio = reserva.getGinasio();
        } else {
            ginasio = parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido.");
        }

        AjusteGinasio ajuste = new AjusteGinasio();
        ajuste.setTenant(tenant);
        ajuste.setGinasio(ginasio);
        ajuste.setData(data);
        ajuste.setTipo(tipo);
        ajuste.setReserva(reserva);
        ajuste.setMotivo(dto.motivo() == null || dto.motivo().isBlank() ? null : dto.motivo().trim());

        ajuste = ajusteRepository.save(ajuste);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAjusteDTO(ajuste, reserva == null));
    }

    @DeleteMapping("/ajustes/{id}")
    public ResponseEntity<Void> excluirAjuste(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar os dias dos ginásios.");
        AjusteGinasio ajuste = ajusteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ajuste não encontrado."));
        segurancaService.garantirAcessoTenant(ajuste.getTenant().getId());

        ajusteRepository.delete(ajuste);
        return ResponseEntity.noContent().build();
    }

    // ============================ HELPERS ============================

    private Tenant tenantAtual() {
        return tenantRepository.findById(segurancaService.tenantDoLogado())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidade não encontrada."));
    }

    private YearMonth parseMes(String valor, YearMonth padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        try {
            return YearMonth.parse(valor.trim(), DateTimeFormatter.ofPattern("uuuu-MM"));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido. Use o formato AAAA-MM.");
        }
    }

    private LocalDate parseData(String valor, String mensagem) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private String obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        return valor.trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> tipo, String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private BigDecimal parseValorDia(BigDecimal valor) {
        if (valor == null) return VALOR_DIA_PADRAO;
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor por dia deve ser maior que zero.");
        }
        return valor;
    }

    private ReservaModalDTO toReservaModalDTO(ReservaGinasio r) {
        BigDecimal valorDia = r.getValorDia() != null ? r.getValorDia() : VALOR_DIA_PADRAO;
        return new ReservaModalDTO(r.getId(), r.getNome(), r.getGinasio().name(), r.getDiaSemana().name(), valorDia);
    }

    private AjusteDTO toAjusteDTO(AjusteGinasio a, boolean geral) {
        return new AjusteDTO(a.getId(), a.getData().toString(), a.getTipo().name(), a.getMotivo(), geral);
    }

    // ============================ DTOs ============================

    record CriarReservaDTO(String ginasio, String diaSemana, String nome, BigDecimal valorDia) {}
    record AtualizarReservaDTO(String ginasio, String diaSemana, String nome, BigDecimal valorDia) {}
    record CriarAjusteDTO(String ginasio, String tipo, String data, UUID reservaId, String motivo) {}
    record ReservaModalDTO(UUID id, String nome, String ginasio, String diaSemana, BigDecimal valorDia) {}

    record DataStatusDTO(String data, String tipo) {}
    record AjusteDTO(UUID id, String data, String tipo, String motivo, boolean geral) {}
    record PessoaGinasioDTO(UUID reservaId, String nome, String ginasio, String diaSemana, BigDecimal valorDia,
                            int diasCobrados, BigDecimal valor,
                            List<DataStatusDTO> datas, List<AjusteDTO> ajustes) {}
    record DiaGinasioDTO(String diaSemana, String rotulo, int qtdeDias, BigDecimal subtotal,
                         List<PessoaGinasioDTO> pessoas, List<AjusteDTO> ajustesGerais) {}
    record GinasioDTO(String ginasio, String nome, BigDecimal total, List<DiaGinasioDTO> dias) {}
    record PeriodoDTO(String mesInicio, String mesFim, String rotulo) {}
    record PainelGinasiosDTO(PeriodoDTO periodo, int totalPessoas, BigDecimal totalGeral,
                             List<GinasioDTO> ginasios) {}
}