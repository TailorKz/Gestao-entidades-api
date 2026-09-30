package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;

public record MesValoresDTO(Integer mes, BigDecimal entradas, BigDecimal saidas) {}