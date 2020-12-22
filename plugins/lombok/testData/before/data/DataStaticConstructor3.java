import lombok.Data;

@Data(staticConstructor="of")
public class DataStaticConstructor3 {
  private int privateInt;

  // Custom private constructor
  private DataStaticConstructor3() {
    this.privateInt = 5;
  }

  public static void main(String[] args) {
    final DataStaticConstructor3 test = new DataStaticConstructor3.of();
    System.out.println(test);
  }
}
