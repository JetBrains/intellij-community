import java.beans.ConstructorProperties;

public class RequiredArgsConstructorTest {

  private final String someProperty;

  @java.beans.ConstructorProperties({"someProperty"})
  public RequiredArgsConstructorTest(String someProperty) {
    this.someProperty = someProperty;
  }

  public static void main(String[] args) {
    final RequiredArgsConstructorTest test = new RequiredArgsConstructorTest("Test");
    System.out.println(test);
  }
}
