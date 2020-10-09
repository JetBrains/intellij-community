package de.plushnikov.wither;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Wither;

@Getter
@Builder
public class Issue473 {
    @Wither final int x;
    @Wither final int y;
}
