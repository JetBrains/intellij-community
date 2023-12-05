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
    private Bar <warning descr="Private field 'bar2' is never assigned">bar2</warning>;
    private Car car;

    public GetterLazyInvocationProduceNPE(Bar bar, Car car) {
      this.bar = bar;
      this.car = car;
    }

    // without warning, because of lazy getter and initialized in constructor
    @Getter(lazy = true)
    private final String barString = bar.sayHello();

    // with warning, because of lazy getter and NOT initialized in constructor
    @Getter(lazy = true)
    private final String bar2String = bar2.<warning descr="Method invocation 'sayHello' will produce 'NullPointerException'">sayHello</warning>();

    //with warning, because of NOT lazy getter
    @Getter
    private final String carString = car.<warning descr="Method invocation 'sayHello' will produce 'NullPointerException'">sayHello</warning>();

}
