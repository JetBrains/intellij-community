package de.plushnikov.delegate;

import lombok.Delegate;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.concurrent.Callable;

public class DelegateRightType<T> {
    @Delegate
    private T delegatorGeneric;

    @Delegate
    private int delegatorPrimitive;

    @Delegate
    private int[] delegatorPrimitiveArray;

    @Delegate
    private Integer[] delegatorArray;

    @Delegate
    private Integer delegatorInteger = 0;

    @RequiredArgsConstructor
    static class MyCallable implements Callable<Integer> {

        @Delegate
        private final Callable<Integer> delegated;
    }

    public static void main(String[] args) throws Exception {
        DelegateRightType<ArrayList> test = new DelegateRightType<ArrayList>();
        test.compareTo(0);

        MyCallable myCallable = new MyCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 1;
            }
        });

        System.out.println(myCallable.call());
    }
}
