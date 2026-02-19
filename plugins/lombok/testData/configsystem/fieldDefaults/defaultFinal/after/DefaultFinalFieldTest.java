import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DefaultFinalFieldTest {
  final int a = 0;
  final String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultFinalFieldTest.class.getDeclaredField("a");
    System.out.println(Modifier.isFinal(field.getModifiers()));
  }
}
