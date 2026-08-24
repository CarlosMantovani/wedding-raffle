package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.RaffleComboUpdateRequest;
import com.weddingraffle.rifa.dto.RaffleConfigResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface RaffleConfigService {

    BigDecimal getCurrentUnitPrice();

    boolean isDrawClosed();

    RaffleConfigResponse getConfig();

    RaffleConfigResponse updateUnitPrice(BigDecimal unitPrice);

    RaffleConfigResponse updateScheduledDrawAt(OffsetDateTime scheduledDrawAt);

    RaffleConfigResponse updateCombo(Long comboId, RaffleComboUpdateRequest request);
}
