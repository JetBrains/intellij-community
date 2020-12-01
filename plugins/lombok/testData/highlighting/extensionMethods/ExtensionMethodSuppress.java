import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Extensions.class)
class ExtensionMethodSuppress {
	public void test() {
		Test.staticMethod();
		
		Test test = new Test();
		test.instanceMethod();
		test.staticMethod();
	}
}

@ExtensionMethod(value = Extensions.class, suppressBaseMethods = false)
class ExtensionMethodKeep {
	public void test() {
		Test.staticMethod();
		
		Test test = new Test();
		test.instanceMethod();
		test.staticMethod();
	}
}

class Test {
	public static void staticMethod() {
		
	}
	
	public void instanceMethod() {
		
	}
}

class Extensions {
	public static void staticMethod(Test test) {
		
	}
	
	public static void instanceMethod(Test test) {
		
	}
}