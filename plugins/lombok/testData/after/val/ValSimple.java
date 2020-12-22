public class ValSimple {
	private String field = "field";
	private short field2 = 5;
	
	private String method() {
		return "method";
	}
	
	private double method2() {
		return 2.0;
	}
	
	private void testVal(String param) {
		final java.lang.String fieldV = field;
		final java.lang.String methodV = method();
		final java.lang.String paramV = param;
		final java.lang.String valOfVal = fieldV;
		final java.lang.String operatorV = fieldV + valOfVal;
		final short fieldW = field2;
		final double methodW = method2();
		byte localVar = 3;
		final int operatorW = fieldW + localVar;
	}
}
