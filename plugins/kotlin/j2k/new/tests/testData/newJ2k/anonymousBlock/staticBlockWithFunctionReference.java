package com.example;

import java.util.function.Function;

public class ConvertTest {
    static Function<char[], String> test;

    static {
        test = String::new;
    }
}