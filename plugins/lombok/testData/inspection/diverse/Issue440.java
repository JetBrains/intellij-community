public class Issue440 {
  private static class Test1 {
    private Car car;

    private final String carString = car.<warning descr="Method invocation 'toString' will produce 'NullPointerException'">toString</warning>();

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

  private static class Bar {
  }

  private static class Car {
  }
}
