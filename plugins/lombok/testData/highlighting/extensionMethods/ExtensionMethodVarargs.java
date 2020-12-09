// version 8:
import lombok.experimental.ExtensionMethod;

@ExtensionMethod(ExtensionMethodVarargs.Extensions.class)
class ExtensionMethodVarargs {
	public void test() {
		Long l1 = 1l;
		long l2 = 1l;
		Integer i1 = 1;
		int i2 = 1;
		
		"%d %d %d %d".format(l1, l2, i1, i2);
		"%d".format(l1);
		"".format(new Integer[]{1,2});
		"".format(new Integer[]{1,2}, new Integer[]{1,2});
	}

	static class Extensions {
		public static String format(String string, Object... params) {
			return String.format(string, params);
		}
	}
}
