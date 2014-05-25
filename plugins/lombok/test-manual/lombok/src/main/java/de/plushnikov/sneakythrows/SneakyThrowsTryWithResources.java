package de.plushnikov.sneakythrows;

import sun.security.pkcs.EncodingException;

import java.io.IOException;

class SneakyThrowsTryWithResources {
    public AutoCloseable foo() throws IOException {
        return null;
    }

    @lombok.SneakyThrows
    public void bar () {
        try (AutoCloseable foo = foo()) {
            // coded
            int a;
        }
    }
}