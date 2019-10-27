public class Issue440 {
  private Bar bar;

  @lombok.Getter(lazy = true)
  private final String barString = bar.toString();

  private Car car;

  private final String carString = car.<warning descr="Method invocation 'toString' will produce 'NullPointerException'">toString</warning>();

  public Issue440(Bar bar) {
    this.bar = bar;
  }

  private static class Bar {

  }

  private static class Car {

  }
}
