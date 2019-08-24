import lombok.Getter;
import lombok.Setter;

public class CompletitionTest {
  @Getter
  @Setter
  private float myFloat;

  public int getInt() {
    return 100;
  }

  public int getInt2() {
    return 100;
  }

  public Integer getInteger() {
    return new Integer(100);
  }

  public static void main(String[] args) {
    CompletitionTest test = new CompletitionTest();
    System.out.println(test.getMyFloat());
    test.getInt();
    float myFloat = test.getMyFloat();
    System.out.println(myFloat);
  }
}
