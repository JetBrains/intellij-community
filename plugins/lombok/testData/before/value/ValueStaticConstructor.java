import lombok.Value;

@Value(staticConstructor="of")
public class ValueStaticConstructor {
  private int privateInt;

  // Custom private constructor
  private ValueStaticConstructor(int privateInt) {
    this.privateInt = -privateInt;
  }

  public static void main(String[] args) {
    final ValueStaticConstructor test = new ValueStaticConstructor.of(1);
    System.out.println(test);
  }
}
