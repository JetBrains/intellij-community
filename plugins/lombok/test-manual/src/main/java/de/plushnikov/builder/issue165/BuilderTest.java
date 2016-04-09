package de.plushnikov.builder.issue165;

public class BuilderTest {
  public static void main(String[] args) {
    System.out.println(CommonBuilder.personBuilder().withTwoKids().firstName("FirstName"));
    System.out.println(CommonBuilder.petBuilder().asMickeMouse().name("Name"));
  }
}
