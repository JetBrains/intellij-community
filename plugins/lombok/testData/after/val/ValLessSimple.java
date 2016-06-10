public class ValLessSimple {
	private short field2 = 5;
	private String method() {
		return "method";
	}
	private double method2() {
		return 2.0;
	}
	{
		System.out.println("Hello");
		final int z = 20;
		final int x = 10;
		final int a = z;
		final short y = field2;
	}
	private void testVal(String param) {
		final java.lang.String fieldV = field;
		final int a = 10;
		final int b = 20;
		{
			final java.lang.String methodV = method();
			final java.lang.String foo = fieldV + methodV;
		}
	}
	private void testValInCatchBlock() {
		try {
			final int x = 1 / 0;
		} catch (ArithmeticException e) {
			final int y = 0;
		}
	}
	private String field = "field";
}