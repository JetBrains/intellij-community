/**
 * {@code RIGHT} has a class body, so it is an instance of the anonymous {@code Alignment$1} while the field
 * {@code RIGHT} is declared in {@code Alignment} - code generation has to use the declared type, not the type of
 * the constant. The static initializer records that it ran, so a test can prove that reading a form does not
 * initialize the enum classes it names.
 */
public enum Alignment {
  LEFT,
  RIGHT {
    @Override
    public int weight() {
      return 1;
    }
  };

  static {
    Payload.record("enum-static-initializer");
  }

  public int weight() {
    return 0;
  }
}
