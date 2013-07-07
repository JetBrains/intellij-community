import lombok.val;

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
		val fieldV = field;
		val methodV = method();
		val paramV = param;
		
		val valOfVal = fieldV;
		val operatorV = fieldV + valOfVal;
		
		val fieldW = field2;
		val methodW = method2();
		byte localVar = 3;
		val operatorW = fieldW + localVar;
	}
}
