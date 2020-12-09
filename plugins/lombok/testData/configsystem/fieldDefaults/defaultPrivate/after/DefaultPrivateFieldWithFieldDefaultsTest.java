import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DefaultPrivateFieldWithFieldDefaultsTest {
  int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultPrivateFieldWithFieldDefaultsTest.class.getDeclaredField("a");
    System.out.println(Modifier.isPrivate(field.getModifiers()));
  }
}
