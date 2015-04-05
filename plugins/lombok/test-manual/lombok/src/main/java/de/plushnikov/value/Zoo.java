package de.plushnikov.value;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Zoo {
    private String meerkat;
    private String warthog;

    public Zoo create() {
        return new Zoo("tomon", "pumbaa");
    }
}
