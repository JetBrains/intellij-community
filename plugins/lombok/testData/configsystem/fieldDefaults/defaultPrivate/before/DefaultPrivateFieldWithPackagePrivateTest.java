import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.experimental.PackagePrivate;

public class DefaultPrivateFieldWithPackagePrivateTest {
  @PackagePrivate int a = 0;
  String b = "";

  public static void main(String[] args) throws Exception {
    Field field = DefaultPrivateFieldWithPackagePrivateTest.class.getDeclaredField("a");
    System.out.println(Modifier.isPrivate(field.getModifiers()));
  }
}
