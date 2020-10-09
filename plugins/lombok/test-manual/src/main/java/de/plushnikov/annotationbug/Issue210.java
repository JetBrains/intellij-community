package de.plushnikov.annotationbug;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Issue210 {
  private String myString;

  public void doLog() {
    log.info("Log from doLog {}", myString);
  }

  public static void main(String[] args) {
    log.info("Log from main");
  }
}
