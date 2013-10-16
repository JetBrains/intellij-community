package de.plushnikov.enumid;

import lombok.EnumId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AnotherStatus {
    WAITING(0),
    READY(1),
    SKIPPED(-1),
    COMPLETED(5);

    @EnumId
    @Getter
    private final int code;
}
