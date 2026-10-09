package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.DiaSemana;
import com.tailorkz.gestao_entidades.domain.enums.Ginasio;
import com.tailorkz.gestao_entidades.domain.model.HorarioGinasio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioGinasioRepository extends JpaRepository<HorarioGinasio, UUID> {

    List<HorarioGinasio> findByTenantId(UUID tenantId);

    List<HorarioGinasio> findByTenantIdAndGinasioOrderByHoraInicioAsc(UUID tenantId, Ginasio ginasio);

    boolean existsByTenantIdAndGinasioAndDiaSemanaAndHoraInicioAndHoraFim(
            UUID tenantId, Ginasio ginasio, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFim);

    boolean existsByTenantIdAndGinasioAndDiaSemanaAndHoraInicioAndHoraFimAndIdNot(
            UUID tenantId, Ginasio ginasio, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFim, UUID id);
}
