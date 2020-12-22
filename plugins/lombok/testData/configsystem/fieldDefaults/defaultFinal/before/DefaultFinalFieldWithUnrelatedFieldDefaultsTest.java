import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE) // implicit makeFinal = false
public class DefaultFinalWithUnrelatedFieldDefaultsFieldTest {
  int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultFinalWithUnrelatedFieldDefaultsFieldTest.class.getDeclaredField("a");
    System.out.println(Modifier.isFinal(field.getModifiers()));
  }
}
