import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DefaultPrivateFieldWithUnrelatedFieldDefaultsTest {
  private final int a = 0;
  private final String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultPrivateFieldWithUnrelatedFieldDefaultsTest.class.getDeclaredField("a");
    System.out.println(Modifier.isPrivate(field.getModifiers()));
  }
}
