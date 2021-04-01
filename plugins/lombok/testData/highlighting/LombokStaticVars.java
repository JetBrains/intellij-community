package org.slf4j;

import lombok.extern.slf4j.Slf4j;

interface <warning descr="Interface 'Logger' is never used">Logger</warning> {
  void info(String msg);
}

@Slf4j
public class LombokStaticVars {
    static {
        log.info("This should not produce error: Variable 'log' might not have been initialized");
    }

    public static final Runnable LAMDA = () -> {
        log.info("This should not produce error: Variable 'log' might not have been initialized");
    };

  public static void main(String[] args) {
    LAMDA.run();
  }
}
