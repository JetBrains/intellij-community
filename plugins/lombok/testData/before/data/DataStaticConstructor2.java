import lombok.Data;

@Data(staticConstructor="of")
public class DataStaticConstructor2 {
  private int privateInt;
  private final int privateFinalInt;

  // Custom private constructor
  private DataStaticConstructor2(int privateFinalInt) {
    this.privateInt = 5;
    this.privateFinalInt = privateFinalInt;
  }

  public static void main(String[] args) {
    final DataStaticConstructor2 test = new DataStaticConstructor2.of();
    System.out.println(test);
  }
}
