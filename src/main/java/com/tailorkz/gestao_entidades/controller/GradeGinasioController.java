package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.DiaSemana;
import com.tailorkz.gestao_entidades.domain.enums.Ginasio;
import com.tailorkz.gestao_entidades.domain.model.EventoGinasio;
import com.tailorkz.gestao_entidades.domain.model.HorarioGinasio;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.repository.EventoGinasioRepository;
import com.tailorkz.gestao_entidades.domain.repository.HorarioGinasioRepository;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ginasios/grade")
@CrossOrigin(origins = "*")
public class GradeGinasioController {

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioGinasioRepository horarioRepository;
    private final EventoGinasioRepository eventoRepository;
    private final TenantRepository tenantRepository;
    private final SegurancaService segurancaService;

    public GradeGinasioController(HorarioGinasioRepository horarioRepository,
                                  EventoGinasioRepository eventoRepository,
                                  TenantRepository tenantRepository,
                                  SegurancaService segurancaService) {
        this.horarioRepository = horarioRepository;
        this.eventoRepository = eventoRepository;
        this.tenantRepository = tenantRepository;
        this.segurancaService = segurancaService;
    }

    // ============================ GRADE ============================

    @GetMapping
    public ResponseEntity<PainelGradeDTO> listarGrade(@RequestParam(value = "ginasio") String ginasioParam) {
        UUID tenantId = segurancaService.tenantDoLogado();
        Ginasio ginasio = parseEnum(Ginasio.class, ginasioParam, "Ginásio inválido.");
        List<HorarioGinasio> horarios = horarioRepository.findByTenantIdAndGinasioOrderByHoraInicioAsc(tenantId, ginasio);
        return ResponseEntity.ok(new PainelGradeDTO(
                ginasio.name(),
                ginasio.getRotulo(),
                horarios.stream().map(GradeGinasioController::toHorarioDTO).toList()));
    }

    @PostMapping
    public ResponseEntity<HorarioDTO> criarHorario(@RequestBody CriarHorarioDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar a grade dos ginásios.");
        Tenant tenant = tenantAtual();
        Ginasio ginasio = parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido.");
        DiaSemana dia = parseEnum(DiaSemana.class, dto.diaSemana(), "Dia da semana inválido.");
        LocalTime inicio = parseHora(dto.horaInicio(), "Horário inicial inválido.");
        LocalTime fim = parseHora(dto.horaFim(), "Horário final inválido.");
        validarIntervalo(inicio, fim);
        String nome = obrigatorio(dto.nome(), "Informe o nome do responsável pelo horário.");
        if (horarioRepository.existsByTenantIdAndGinasioAndDiaSemanaAndHoraInicioAndHoraFim(
                tenant.getId(), ginasio, dia, inicio, fim)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um responsável nesse horário. Se for o mesmo grupo, edite o cadastro existente.");
        }

        HorarioGinasio horario = new HorarioGinasio();
        horario.setTenant(tenant);
        horario.setGinasio(ginasio);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(inicio);
        horario.setHoraFim(fim);
        horario.setNome(nome);
        horario.setObservacao(textoOpcional(dto.observacao()));
        horario = horarioRepository.save(horario);

        return ResponseEntity.status(HttpStatus.CREATED).body(toHorarioDTO(horario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HorarioDTO> atualizarHorario(@PathVariable UUID id, @RequestBody AtualizarHorarioDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar a grade dos ginásios.");
        HorarioGinasio horario = horarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário não encontrado."));
        segurancaService.garantirAcessoTenant(horario.getTenant().getId());

        if (dto.diaSemana() != null && !dto.diaSemana().isBlank()) {
            horario.setDiaSemana(parseEnum(DiaSemana.class, dto.diaSemana(), "Dia da semana inválido."));
        }
        LocalTime inicio = dto.horaInicio() != null && !dto.horaInicio().isBlank()
                ? parseHora(dto.horaInicio(), "Horário inicial inválido.") : horario.getHoraInicio();
        LocalTime fim = dto.horaFim() != null && !dto.horaFim().isBlank()
                ? parseHora(dto.horaFim(), "Horário final inválido.") : horario.getHoraFim();
        validarIntervalo(inicio, fim);
        if (horarioRepository.existsByTenantIdAndGinasioAndDiaSemanaAndHoraInicioAndHoraFimAndIdNot(
                horario.getTenant().getId(), horario.getGinasio(), horario.getDiaSemana(), inicio, fim, horario.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um responsável nesse horário. Se for o mesmo grupo, edite o cadastro existente.");
        }
        horario.setHoraInicio(inicio);
        horario.setHoraFim(fim);
        if (dto.nome() != null && !dto.nome().isBlank()) horario.setNome(dto.nome().trim());
        if (dto.observacao() != null) horario.setObservacao(textoOpcional(dto.observacao()));

        horario = horarioRepository.save(horario);
        return ResponseEntity.ok(toHorarioDTO(horario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirHorario(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar a grade dos ginásios.");
        HorarioGinasio horario = horarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário não encontrado."));
        segurancaService.garantirAcessoTenant(horario.getTenant().getId());
        horarioRepository.delete(horario);
        return ResponseEntity.noContent().build();
    }

    // ============================ EVENTOS ============================

    @GetMapping("/eventos")
    public ResponseEntity<List<EventoDTO>> listarEventos(@RequestParam(value = "ginasio", required = false) String ginasioParam) {
        UUID tenantId = segurancaService.tenantDoLogado();
        List<EventoGinasio> eventos;
        if (ginasioParam != null && !ginasioParam.isBlank()) {
            Ginasio ginasio = parseEnum(Ginasio.class, ginasioParam, "Ginásio inválido.");
            eventos = eventoRepository.findByTenantIdAndGinasioOrderByDataInicioAsc(tenantId, ginasio);
        } else {
            eventos = eventoRepository.findByTenantIdOrderByDataInicioAsc(tenantId);
        }
        Map<Ginasio, List<HorarioGinasio>> horariosPorGinasio = new EnumMap<>(Ginasio.class);
        for (HorarioGinasio h : horarioRepository.findByTenantId(tenantId)) {
            horariosPorGinasio.computeIfAbsent(h.getGinasio(), k -> new ArrayList<>()).add(h);
        }
        return ResponseEntity.ok(eventos.stream()
                .map(e -> toEventoDTO(e, horariosPorGinasio.getOrDefault(e.getGinasio(), List.of())))
                .toList());
    }

    @PostMapping("/eventos")
    public ResponseEntity<EventoDTO> criarEvento(@RequestBody CriarEventoDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar os eventos dos ginásios.");
        Tenant tenant = tenantAtual();
        Ginasio ginasio = parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido.");
        String titulo = obrigatorio(dto.titulo(), "Informe o título do evento.");
        LocalDate inicio = parseData(dto.dataInicio(), "Data inicial inválida. Use o formato AAAA-MM-DD.");
        LocalDate fim = dto.dataFim() == null || dto.dataFim().isBlank()
                ? inicio : parseData(dto.dataFim(), "Data final inválida. Use o formato AAAA-MM-DD.");
        validarPeriodo(inicio, fim);
        LocalTime horaInicio = parseHoraOpcional(dto.horaInicio(), "Horário inicial inválido.");
        LocalTime horaFim = parseHoraOpcional(dto.horaFim(), "Horário final inválido.");
        validarHorasOpcionais(horaInicio, horaFim);

        EventoGinasio evento = new EventoGinasio();
        evento.setTenant(tenant);
        evento.setGinasio(ginasio);
        evento.setTitulo(titulo);
        evento.setDescricao(textoOpcional(dto.descricao()));
        evento.setDataInicio(inicio);
        evento.setDataFim(fim);
        evento.setHoraInicio(horaInicio);
        evento.setHoraFim(horaFim);
        evento = eventoRepository.save(evento);

        List<HorarioGinasio> horarios =
                horarioRepository.findByTenantIdAndGinasioOrderByHoraInicioAsc(tenant.getId(), ginasio);
        return ResponseEntity.status(HttpStatus.CREATED).body(toEventoDTO(evento, horarios));
    }

    @PutMapping("/eventos/{id}")
    public ResponseEntity<EventoDTO> atualizarEvento(@PathVariable UUID id, @RequestBody AtualizarEventoDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar os eventos dos ginásios.");
        EventoGinasio evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        segurancaService.garantirAcessoTenant(evento.getTenant().getId());

        if (dto.ginasio() != null && !dto.ginasio().isBlank()) {
            evento.setGinasio(parseEnum(Ginasio.class, dto.ginasio(), "Ginásio inválido."));
        }
        if (dto.titulo() != null && !dto.titulo().isBlank()) evento.setTitulo(dto.titulo().trim());
        if (dto.descricao() != null) evento.setDescricao(textoOpcional(dto.descricao()));

        LocalDate inicio = dto.dataInicio() != null && !dto.dataInicio().isBlank()
                ? parseData(dto.dataInicio(), "Data inicial inválida. Use o formato AAAA-MM-DD.") : evento.getDataInicio();
        LocalDate fim = dto.dataFim() != null && !dto.dataFim().isBlank()
                ? parseData(dto.dataFim(), "Data final inválida. Use o formato AAAA-MM-DD.") : evento.getDataFim();
        validarPeriodo(inicio, fim);
        evento.setDataInicio(inicio);
        evento.setDataFim(fim);

        LocalTime horaInicio = dto.horaInicio() != null
                ? parseHoraOpcional(dto.horaInicio(), "Horário inicial inválido.") : evento.getHoraInicio();
        LocalTime horaFim = dto.horaFim() != null
                ? parseHoraOpcional(dto.horaFim(), "Horário final inválido.") : evento.getHoraFim();
        validarHorasOpcionais(horaInicio, horaFim);
        evento.setHoraInicio(horaInicio);
        evento.setHoraFim(horaFim);

        evento = eventoRepository.save(evento);
        List<HorarioGinasio> horarios =
                horarioRepository.findByTenantIdAndGinasioOrderByHoraInicioAsc(evento.getTenant().getId(), evento.getGinasio());
        return ResponseEntity.ok(toEventoDTO(evento, horarios));
    }

    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> excluirEvento(@PathVariable UUID id) {
        segurancaService.garantirEhGestor("Somente gestores podem administrar os eventos dos ginásios.");
        EventoGinasio evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        segurancaService.garantirAcessoTenant(evento.getTenant().getId());
        eventoRepository.delete(evento);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eventos/{id}/afetados")
    public ResponseEntity<List<AfetadoDTO>> afetados(@PathVariable UUID id) {
        UUID tenantId = segurancaService.tenantDoLogado();
        EventoGinasio evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        segurancaService.garantirAcessoTenant(evento.getTenant().getId());
        return ResponseEntity.ok(calcularAfetados(evento.getGinasio(), evento.getDataInicio(), evento.getDataFim(),
                evento.getHoraInicio(), evento.getHoraFim(),
                horarioRepository.findByTenantIdAndGinasioOrderByHoraInicioAsc(tenantId, evento.getGinasio())));
    }

    // ============================ HELPERS ============================

    private List<AfetadoDTO> calcularAfetados(Ginasio ginasio, LocalDate inicio, LocalDate fim,
                                              LocalTime horaInicio, LocalTime horaFim, List<HorarioGinasio> horarios) {
        List<AfetadoDTO> afetados = new ArrayList<>();
        if (inicio == null || fim == null) return afetados;
        boolean filtraHora = horaInicio != null && horaFim != null;
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            DiaSemana dia = DiaSemana.de(data.getDayOfWeek());
            for (HorarioGinasio h : horarios) {
                if (h.getGinasio() != ginasio || h.getDiaSemana() != dia) continue;
                if (filtraHora && h.getHoraInicio() != null && h.getHoraFim() != null) {
                    boolean sobrepoe = h.getHoraInicio().isBefore(horaFim) && h.getHoraFim().isAfter(horaInicio);
                    if (!sobrepoe) continue;
                }
                afetados.add(new AfetadoDTO(
                        h.getId(), h.getNome(), dia.name(), dia.getRotulo(),
                        fmtHora(h.getHoraInicio()), fmtHora(h.getHoraFim()), data.toString()));
            }
        }
        return afetados;
    }

    private Tenant tenantAtual() {
        UUID tenantId = segurancaService.tenantDoLogado();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidade não encontrada."));
    }

    private static String obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        return valor.trim();
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> tipo, String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private static LocalTime parseHora(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        return parseHoraOpcional(valor, mensagem);
    }

    private static LocalTime parseHoraOpcional(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalTime.parse(valor.trim(), HORA_FMT);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private static LocalDate parseData(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private static void validarIntervalo(LocalTime inicio, LocalTime fim) {
        if (!fim.isAfter(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário final deve ser depois do inicial.");
        }
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data final não pode ser antes da inicial.");
        }
    }

    private static void validarHorasOpcionais(LocalTime inicio, LocalTime fim) {
        if ((inicio == null) != (fim == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o horário inicial e final, ou nenhum dos dois.");
        }
        if (inicio != null) validarIntervalo(inicio, fim);
    }

    private static String fmtHora(LocalTime hora) {
        return hora == null ? null : hora.format(HORA_FMT);
    }

    private static HorarioDTO toHorarioDTO(HorarioGinasio h) {
        return new HorarioDTO(h.getId(), h.getGinasio().name(), h.getDiaSemana().name(),
                fmtHora(h.getHoraInicio()), fmtHora(h.getHoraFim()), h.getNome(), h.getObservacao());
    }

    private EventoDTO toEventoDTO(EventoGinasio e, List<HorarioGinasio> horarios) {
        return new EventoDTO(e.getId(), e.getGinasio().name(), e.getTitulo(), e.getDescricao(),
                e.getDataInicio().toString(), e.getDataFim().toString(),
                fmtHora(e.getHoraInicio()), fmtHora(e.getHoraFim()),
                calcularAfetados(e.getGinasio(), e.getDataInicio(), e.getDataFim(),
                        e.getHoraInicio(), e.getHoraFim(), horarios));
    }

    // ============================ DTOs ============================

    record CriarHorarioDTO(String ginasio, String diaSemana, String horaInicio, String horaFim,
                           String nome, String observacao) {}

    record AtualizarHorarioDTO(String diaSemana, String horaInicio, String horaFim,
                               String nome, String observacao) {}

    record HorarioDTO(UUID id, String ginasio, String diaSemana, String horaInicio, String horaFim,
                      String nome, String observacao) {}

    record PainelGradeDTO(String ginasio, String rotulo, List<HorarioDTO> horarios) {}

    record AfetadoDTO(UUID horarioId, String nome, String diaSemana, String diaRotulo,
                      String horaInicio, String horaFim, String data) {}

    record CriarEventoDTO(String ginasio, String titulo, String descricao, String dataInicio,
                          String dataFim, String horaInicio, String horaFim) {}

    record AtualizarEventoDTO(String ginasio, String titulo, String descricao, String dataInicio,
                              String dataFim, String horaInicio, String horaFim) {}

    record EventoDTO(UUID id, String ginasio, String titulo, String descricao, String dataInicio,
                     String dataFim, String horaInicio, String horaFim, List<AfetadoDTO> afetados) {}
}
