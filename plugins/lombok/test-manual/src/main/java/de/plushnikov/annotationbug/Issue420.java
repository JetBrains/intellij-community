package de.plushnikov.annotationbug;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class Issue420 implements AutoCloseable{
  private final int someInt;
  private final float someFloat;
  private final String someString;
  private final String someString2;

  @Override
  public void close() throws Exception {

  }

  public static void main(String[] args) {
    Issue420 issue420 = new Issue420(1, 2, "", "");
    issue420.getSomeInt();
    issue420.getSomeFloat();
    issue420.getSomeString();
    System.out.println(issue420);
  }
}
