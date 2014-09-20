package de.plushnikov.lazygetter;

import lombok.Getter;

public class Hoge1 {
    private final String hoge;

    @Getter(lazy = true)
    private final String method = hoge;

    public Hoge1(String hoge) {
        this.hoge = hoge;
    }
}
