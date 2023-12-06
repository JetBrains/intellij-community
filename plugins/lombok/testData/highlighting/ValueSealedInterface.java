import lombok.Value;

sealed interface Sealed permits ValueSealedInterface, ValueSealedInterface.A, ValueSealedInterface.B, ValueSealedInterface.C {
}

@Value
public class ValueSealedInterface implements Sealed {

  static final class A implements Sealed {

  }

  @Value
  static class B implements Sealed {
    // should not report: "sealed, non-sealed or final modifiers expected" even though class is final
  }

  @Value
  static final class C implements Sealed {

  }
}