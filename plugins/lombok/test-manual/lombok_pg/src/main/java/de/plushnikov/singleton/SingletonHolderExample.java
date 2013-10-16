package de.plushnikov.singleton;

import lombok.Singleton;

@Singleton(style=Singleton.Style.HOLDER)
public class SingletonHolderExample {
    private String s;

    public void foo() {
    }
}
