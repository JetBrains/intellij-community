package de.plushnikov.data;


import lombok.Data;

@Data
public class DataWithGenerics<T> {

    private final T command;
    private final Runnable callback;

    public static void main(String[] args) {
        DataWithGenerics<Integer> test = new DataWithGenerics<Integer>(123, new Runnable() {
            @Override
            public void run() {
                System.out.println("Run");
            }
        });

        test.getCallback();
        test.getCommand();

        DataWithGenerics<String> foo = new DataWithGenerics<String>("foo", new Runnable() {
            public void run() {
            }
        });
    }
}
