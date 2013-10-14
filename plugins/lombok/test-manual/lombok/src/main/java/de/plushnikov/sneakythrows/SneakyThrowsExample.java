package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.UnsupportedEncodingException;

public class SneakyThrowsExample implements Runnable {

    @SneakyThrows({UnsatisfiedLinkError.class, UnsupportedEncodingException.class})
    public String utf8ToString(byte[] bytes) throws IllegalAccessException{
        if(1==1) {
            return new String(bytes, "UTF-8");
        }else{
            throw new IllegalAccessException();
        }
    }


    @SneakyThrows
    public void run() {
        throw new Throwable();
    }

    public static void main(String[] args) throws IllegalAccessException{
        SneakyThrowsExample example = new SneakyThrowsExample();

        System.out.println(example.utf8ToString("Test".getBytes()));

        example.run();
    }
}
