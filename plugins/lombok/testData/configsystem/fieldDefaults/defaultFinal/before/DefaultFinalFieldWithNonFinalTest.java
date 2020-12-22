import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.experimental.NonFinal;

public class DefaultFinalFieldTestWithNonFinalTest {
  @NonFinal int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultFinalFieldTestWithNonFinalTest.class.getDeclaredField("a");
    System.out.println(Modifier.isFinal(field.getModifiers()));
  }
}
