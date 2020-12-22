import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DefaultFinalFieldTest {
  int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultFinalFieldTest.class.getDeclaredField("a");
    System.out.println(Modifier.isFinal(field.getModifiers()));
  }
}
