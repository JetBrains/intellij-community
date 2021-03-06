import lombok.experimental.ExtensionMethod;

@ExtensionMethod({ExtensionMethodAutoboxing.Extensions.class})
class ExtensionMethodAutoboxing {
	public void test() {
		Long l1 = 1l;
		long l2 = 1l;
		Integer i1 = 1;
		int i2 = 1;
		
		String string = "test";
		string.boxing(l1, i1);
		string.boxing(l1, i2);
		string.boxing(l2, i1);
		string.boxing(l2, i2);
	}
	
	static class Extensions {
		public static String boxing(String string, Long a, int b) {
			return string + " " + a + " " + b;
		}
	}
}
