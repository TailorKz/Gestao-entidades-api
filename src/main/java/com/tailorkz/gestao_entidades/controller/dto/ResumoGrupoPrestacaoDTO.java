package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumoGrupoPrestacaoDTO(GrupoPrestacaoDTO grupo,
                                      BigDecimal entradas,
                                      BigDecimal saidas,
                                      BigDecimal saldo,
                                      Long quantidade,
                                      List<MesValoresDTO> porMes) {}