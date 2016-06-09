import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequiredArgsConstructorTest {

  private final String someProperty;

  public static void main(String[] args) {
    final RequiredArgsConstructorTest test = new RequiredArgsConstructorTest("Test");
    System.out.println(test);
  }
}