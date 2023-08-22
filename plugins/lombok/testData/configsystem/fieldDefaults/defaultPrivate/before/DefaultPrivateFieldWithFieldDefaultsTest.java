import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PACKAGE)
public class DefaultPrivateFieldWithFieldDefaultsTest {
  int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultPrivateFieldWithFieldDefaultsTest.class.getDeclaredField("a");
    System.out.println(Modifier.isPrivate(field.getModifiers()));
  }
}
