package com.klodit.soumission_service.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Génère une référence unique pour chaque soumission.
 * Format : SOUM-YYYYMMDD-XXXXX (ex: SOUM-20260227-00001)
 */
public final class ReferenceGenerator {

    private static final AtomicLong counter = new AtomicLong(0);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ReferenceGenerator() {
    }

    public static String generate() {
        String date = LocalDate.now().format(DATE_FMT);
        long seq = counter.incrementAndGet();
        return String.format("SOUM-%s-%05d", date, seq);
    }
}
