public class Issue37 {

  @lombok.EqualsAndHashCode
  class Car {

  }

  @lombok.EqualsAndHashCode(callSuper = true)
  class Ferrari extends Car {

  }
}
