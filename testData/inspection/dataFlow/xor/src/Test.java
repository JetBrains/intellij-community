public class Test {
  public static void test(final Object a, final Object b) {
	if ((a == null ^ b == null)
		|| (a != null && a.hashCode() != b.hashCode())) {
		System.out.println("aaa");
	}
  }
}