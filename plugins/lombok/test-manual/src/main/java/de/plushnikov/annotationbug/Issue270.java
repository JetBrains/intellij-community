package de.plushnikov.annotationbug;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(exclude = {"someFloat"})
public class Issue270 {
  private int someInt;
  private float someFloat;
  private String someString;

  public static void main(String[] args) {
    Issue270 issue270 = new Issue270();
    issue270.getSomeInt();
    issue270.getSomeFloat();
    issue270.getSomeString();
    System.out.println(issue270);
  }
}
