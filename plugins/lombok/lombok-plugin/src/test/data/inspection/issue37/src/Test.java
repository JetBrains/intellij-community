
public class Test {

  @lombok.EqualsAndHashCode
  class Car {

  }

  @lombok.EqualsAndHashCode(callSuper = true)
  class Ferrari extends Car {

  }
}
