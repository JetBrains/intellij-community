import lombok.val;

public class ValInFor {
	public void enhancedFor() {
		java.util.List<String> list = java.util.Arrays.asList("Hello, World!");
		for (val shouldBeString : list) {
			System.out.println(shouldBeString.toLowerCase());
			val shouldBeString2 = shouldBeString;
		}
	}
}