package de.plushnikov.value;

import lombok.Value;
import lombok.experimental.Wither;

@Wither
@Value
public class ValueAndWither {
    private final String myField;

    public void methodCallingWith() {
        this.withMyField("");
    }
}
