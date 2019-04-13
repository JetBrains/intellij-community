public class NoArgsConstructorTest {

  private String someProperty;

  public NoArgsConstructorTest() {
  }

  public static void main(String[] args) {
    final NoArgsConstructorTest test = new NoArgsConstructorTest();
    System.out.println(test);
  }
}