package a;

import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Extensions.class)
class ExtensionMethodNames {
	
	public void instanceCalls() {
		(new Test()).ext();
		
		Test t = new Test();
		t.ext();
		
		Test Test = new Test();
		Test.ext();
	}
	
	public void staticCalls() {
		Test.ext();
		a.Test.ext();
	}
}

class Extensions {
	public static void ext(Test t) {
	}
}

class Test {
	public static void ext() {
		
	}
}