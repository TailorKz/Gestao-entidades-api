package com.tailorkz.gestao_entidades.domain.enums;

public enum Ginasio {

    ARTHUR_FRIDRICH("Ginásio Arthur Friedrich"),
    POLIESPORTIVO("Ginásio Poliesportivo");

    private final String rotulo;

    Ginasio(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}