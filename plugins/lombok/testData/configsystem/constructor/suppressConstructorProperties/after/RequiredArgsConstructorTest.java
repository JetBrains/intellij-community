public class RequiredArgsConstructorTest {

  private final String someProperty;

  public RequiredArgsConstructorTest(String someProperty) {
    this.someProperty = someProperty;
  }

  public static void main(String[] args) {
    final RequiredArgsConstructorTest test = new RequiredArgsConstructorTest("Test");
    System.out.println(test);
  }
}