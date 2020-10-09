package de.plushnikov.inspection;

public class Issue440 {
  private static class Test1 {
    private Car car;

    private final String carString = car.toString();

    public Test1(Car car) {
      this.car = car;
    }
  }

  private static class Test2 {
    private Bar bar;

    @lombok.Getter(lazy = true)
    private final String barString = bar.toString();

    public Test2(Bar bar) {
      this.bar = bar;
    }
  }

  private final String someString = ((String) null).toString();

  private static class Bar {
  }

  private static class Car {
  }
}
