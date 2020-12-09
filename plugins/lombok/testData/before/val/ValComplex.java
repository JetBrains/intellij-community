import lombok.val;

public class ValComplex {
	private String field = "";
	private static final int CONSTANT = 20;
	
	public void testComplex() {
		val shouldBeCharArray = field.toCharArray();
		val shouldBeInt = CONSTANT;
		val lock = new Object();
		synchronized (lock) {
			val field = 20; //Shadowing
			val inner = 10;
			switch (field) {
				case 5:
					val shouldBeCharArray2 = shouldBeCharArray;
					val innerInner = inner;
			}
		}
		val shouldBeString = field; //Unshadowing
	}
}