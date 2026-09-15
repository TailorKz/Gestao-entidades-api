package com.tailorkz.gestao_entidades.domain.enums;

import java.time.DayOfWeek;

public enum DiaSemana {

    SEGUNDA("Segunda-feira", DayOfWeek.MONDAY),
    TERCA("Terça-feira", DayOfWeek.TUESDAY),
    QUARTA("Quarta-feira", DayOfWeek.WEDNESDAY),
    QUINTA("Quinta-feira", DayOfWeek.THURSDAY),
    SEXTA("Sexta-feira", DayOfWeek.FRIDAY),
    SABADO("Sábado", DayOfWeek.SATURDAY),
    DOMINGO("Domingo", DayOfWeek.SUNDAY);

    private final String rotulo;
    private final DayOfWeek dayOfWeek;

    DiaSemana(String rotulo, DayOfWeek dayOfWeek) {
        this.rotulo = rotulo;
        this.dayOfWeek = dayOfWeek;
    }

    public String getRotulo() {
        return rotulo;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }
}