import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true)
public class DefaultPrivateFieldWithUnrelatedFieldDefaultsTest {
  int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultPrivateFieldWithUnrelatedFieldDefaultsTest.class.getDeclaredField("a");
    System.out.println(Modifier.isPrivate(field.getModifiers()));
  }
}
