package de.plushnikov.constructor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Timer;

@Data
@NoArgsConstructor
public class DataAndOtherConstructor {
  int test1;
  int test2;

  public void test() {
    DataAndOtherConstructor test = new DataAndOtherConstructor();
    System.out.println(test);
    DataAndAllArgsConstructor test2 = new DataAndAllArgsConstructor(1, 2);
    System.out.println(test2);
    DataAndRequiredArgsConstructor test3 = new DataAndRequiredArgsConstructor();
    System.out.println(test3);

    SomeClass someClass = new SomeClass();
  }

  @Data
  @AllArgsConstructor
  class DataAndAllArgsConstructor {
    int test1;
    int test2;
  }

  @Data
  @RequiredArgsConstructor
  class DataAndRequiredArgsConstructor {
    int test1;
    int test2;
  }

  public static void main(String[] args) {
    new DataAndOtherConstructor().test();
  }

  @EqualsAndHashCode(callSuper = false)
  @Data
  class SomeClass extends Timer {
    final int x;

    SomeClass() {
      super();
      x = 3;
    }
  }
}