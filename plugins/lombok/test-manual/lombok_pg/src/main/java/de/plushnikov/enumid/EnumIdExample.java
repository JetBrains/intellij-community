package de.plushnikov.enumid;

import lombok.EnumId;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

public class EnumIdExample {
    @RequiredArgsConstructor
    public enum Status {
        WAITING(0),
        READY(1),
        SKIPPED(-1),
        COMPLETED(5);

        @EnumId
        @Getter
        private final int code;
    }

    public static void main(String[] args) {
        Status x = Status.COMPLETED;
        x = Status.findByCode(0);
        System.out.println(Status.COMPLETED.getCode());
        System.out.println(x);
    }
}
