package de.plushnikov.lazygetter;

import lombok.LazyGetter;

public class Hoge2 {
    private final String hoge;

    @LazyGetter
    private final String method = hoge;

    public Hoge2(String hoge) {
        this.hoge = hoge;
    }
}
