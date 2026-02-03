import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DefaultFinalWithUnrelatedFieldDefaultsFieldTest {
  private int a = 0;
  private String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultFinalWithUnrelatedFieldDefaultsFieldTest.class.getDeclaredField("a");
    System.out.println(Modifier.isFinal(field.getModifiers()));
  }
}
