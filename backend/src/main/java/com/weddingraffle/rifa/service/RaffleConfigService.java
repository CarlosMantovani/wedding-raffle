package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.RaffleComboUpdateRequest;
import com.weddingraffle.rifa.dto.RaffleConfigResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface RaffleConfigService {

    BigDecimal getCurrentUnitPrice();

    OffsetDateTime getWeddingEventAt();

    boolean isDrawClosed();

    RaffleConfigResponse getConfig();

    RaffleConfigResponse updateUnitPrice(BigDecimal unitPrice);

    RaffleConfigResponse updateScheduledDrawAt(OffsetDateTime scheduledDrawAt);

    RaffleConfigResponse updateWeddingEventAt(OffsetDateTime weddingEventAt);

    RaffleConfigResponse updateCombo(Long comboId, RaffleComboUpdateRequest request);
}
