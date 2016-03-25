package de.plushnikov.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Timer;

@Data
public class DataExtendsTest extends Timer {
    float ffff;
    char ccc;

    @Data
    @EqualsAndHashCode(callSuper = false)
    class X extends Timer {
        int iiii;
    }
}
