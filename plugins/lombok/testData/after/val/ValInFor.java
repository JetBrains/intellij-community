public class ValInFor {
	public void enhancedFor() {
		java.util.List<String> list = java.util.Arrays.asList("Hello, World!");
		for (final java.lang.String shouldBeString : list) {
			System.out.println(shouldBeString.toLowerCase());
			final java.lang.String shouldBeString2 = shouldBeString;
		}
	}
}