package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.List;

public record RelatorioPrestacaoDTO(BigDecimal totalEntradas,
                                    BigDecimal totalSaidas,
                                    BigDecimal totalSaldo,
                                    Long totalLancamentos,
                                    List<ResumoGrupoPrestacaoDTO> grupos) {}