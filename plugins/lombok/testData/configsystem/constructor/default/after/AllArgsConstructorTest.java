public class AllArgsConstructorTest {

  private final String someProperty;

  public AllArgsConstructorTest(String someProperty) {
    this.someProperty = someProperty;
  }

  public static void main(String[] args) {
    final AllArgsConstructorTest test = new AllArgsConstructorTest("Test");
    System.out.println(test);
  }
}