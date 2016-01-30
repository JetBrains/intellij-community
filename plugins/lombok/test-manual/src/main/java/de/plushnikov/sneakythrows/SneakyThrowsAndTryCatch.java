package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.IOException;

public class SneakyThrowsAndTryCatch {
    @SneakyThrows
    private void hideIt() {
        try {
            itThrows(true);
        } catch (IOException ex) {   // <=== IntelliJ flags this catch as an error
            ex.printStackTrace();
        }
    }

    private void itThrows(boolean foo) throws Exception {
        if (foo) {
            throw new IOException("test");
        } else {
            throw new Exception("test2");
        }
    }
}
