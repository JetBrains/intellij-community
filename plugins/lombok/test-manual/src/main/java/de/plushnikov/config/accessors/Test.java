package de.plushnikov.config.accessors;

import lombok.Data;

import java.util.Date;

public class Test {
  @Data
  public static class AuthEvent {
    private String ip;
    private Date dateTime;
  }

  public static void main(String[] args) {
    new AuthEvent().setIp("").setDateTime(new Date());
  }
}
