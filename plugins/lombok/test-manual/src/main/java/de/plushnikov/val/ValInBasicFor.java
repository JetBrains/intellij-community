package de.plushnikov.val;

public class ValInBasicFor {
	public void basicFor() {
		java.util.List<String> list = java.util.Arrays.asList("Hello, World!");
		//'val' is not allowed in old-style for loops.
//		for (val shouldBe = 1, marked = "", error = 1.0; ; ) {
//			System.out.println("");
//		}
	}
}