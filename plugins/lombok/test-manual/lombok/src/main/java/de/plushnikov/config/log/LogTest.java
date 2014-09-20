package de.plushnikov.config.log;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        new LogTest().logSomething();
    }
}
