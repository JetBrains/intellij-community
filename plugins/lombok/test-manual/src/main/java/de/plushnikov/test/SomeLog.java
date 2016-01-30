package de.plushnikov.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SomeLog {

    private static final Logger log = LoggerFactory.getLogger(SomeLog.class);

    public void doSomeThing(){
        log.info("some method was called");
    }

    public static void main(String[] args) {
        new SomeLog().doSomeThing();
    }
}
