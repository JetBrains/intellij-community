class With1 {
	@lombok.With boolean foo;
	
	void withFoo(boolean foo) {
	}
	
	With1(boolean foo) {
	}
}

class With2 {
	@lombok.With boolean foo;
	
	void withFoo(String foo) {
	}
	
	With2(boolean foo) {
	}
}

class With3 {
	@lombok.With String foo;
	
	void withFoo(boolean foo) {
	}
	
	With3(String foo) {
	}
}

class With4 {
	@lombok.With String foo;
	
	void withFoo(String foo) {
	}
	
	With4(String foo) {
	}
}

class With5 {
	@lombok.With String foo;
	
	void withFoo() {
	}
	
	With5(String foo) {
	}
}

class With6 {
	@lombok.With String foo;
	
	void withFoo(String foo, int x) {
	}
	
	With6(String foo) {
	}
}

class With7 {
	@lombok.With String foo;
	
	void withFoo(String foo, Object... x) {
	}
	
	With7(String foo) {
	}
}

class With8 {
	@lombok.With boolean isFoo;
	
	void withIsFoo(boolean foo) {
	}
	
	With8(boolean foo) {
	}
}

class With9 {
	@lombok.With boolean isFoo;
	
	void withFoo(boolean foo) {
	}
	
	With9(boolean foo) {
	}
}
