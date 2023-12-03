import lombok.Getter;

public class GetterLazyInvocationProduceNPE {
    private static class Bar {
      public String sayHello() {
        return "Bar{}";
      }
    }

    private static class Car {
      public String sayHello() {
        return "Car{}";
      }
    }

    private Bar <warning descr="Field 'bar' may be 'final'">bar</warning>;
    private Car car;

    public GetterLazyInvocationProduceNPE(Bar bar, Car car) {
      this.bar = bar;
      this.car = car;
    }

    // without warning
    @Getter(lazy = true)
    private final String barString = bar.sayHello();

    //with warining!
    @Getter
    private final String carString = car.<warning descr="Method invocation 'sayHello' will produce 'NullPointerException'">sayHello</warning>();

}
