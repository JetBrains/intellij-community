public class ValComplex {
	private String field = "";
	private static final int CONSTANT = 20;
	public void testComplex() {
		final char[] shouldBeCharArray = field.toCharArray();
		final int shouldBeInt = CONSTANT;
		final java.lang.Object lock = new Object();
		synchronized (lock) {
			final int field = 20; //Shadowing
			final int inner = 10;
			switch (field) {
			case 5: 
				final char[] shouldBeCharArray2 = shouldBeCharArray;
				final int innerInner = inner;
			
			}
		}
		final java.lang.String shouldBeString = field; //Unshadowing
	}
}