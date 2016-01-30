package de.plushnikov.delegate;

import lombok.Delegate;

public class GenericTest implements GenericInterface {

    @Delegate
    private GenericInterface someVar;

    public void doIt2e() {

    }

}
