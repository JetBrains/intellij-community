import lombok.experimental.ExtensionMethod;

@ExtensionMethod({Extensions.class, java.util.Arrays.class})
public class ExtensionMethodDfa {
  public String test() {
    String iAmNull = null;
    return iAmNull.or("hELlO, WORlD!".toTitleCase());
  }

  public void testArrays() {
    String[] arr = null;
    String str = null;
    <warning descr="Passing 'null' argument to parameter annotated as @NotNull">arr</warning>.fill(str);
  }
}
class Extensions {
  public static <T> T or(T obj, T ifNull) {
    return obj != null ? obj : ifNull;
  }

  public static String toTitleCase(String in) {
    if (in.isEmpty()) return in;
    return "" + Character.toTitleCase(in.charAt(0)) +
           in.substring(1).toLowerCase();
  }
}
