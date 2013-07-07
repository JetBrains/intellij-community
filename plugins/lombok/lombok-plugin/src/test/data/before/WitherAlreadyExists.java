class Wither1 {
	@lombok.experimental.Wither boolean foo;
	
	void withFoo(boolean foo) {
	}
	
	Wither1(boolean foo) {
	}
}

class Wither2 {
	@lombok.experimental.Wither boolean foo;
	
	void withFoo(String foo) {
	}
	
	Wither2(boolean foo) {
	}
}

class Wither3 {
	@lombok.experimental.Wither String foo;
	
	void withFoo(boolean foo) {
	}
	
	Wither3(String foo) {
	}
}

class Wither4 {
	@lombok.experimental.Wither String foo;
	
	void withFoo(String foo) {
	}
	
	Wither4(String foo) {
	}
}

class Wither5 {
	@lombok.experimental.Wither String foo;
	
	void withFoo() {
	}
	
	Wither5(String foo) {
	}
}

class Wither6 {
	@lombok.experimental.Wither String foo;
	
	void withFoo(String foo, int x) {
	}
	
	Wither6(String foo) {
	}
}

class Wither7 {
	@lombok.experimental.Wither String foo;
	
	void withFoo(String foo, Object... x) {
	}
	
	Wither7(String foo) {
	}
}

class Wither8 {
	@lombok.experimental.Wither boolean isFoo;
	
	void withIsFoo(boolean foo) {
	}
	
	Wither8(boolean foo) {
	}
}

class Wither9 {
	@lombok.experimental.Wither boolean isFoo;
	
	void withFoo(boolean foo) {
	}
	
	Wither9(boolean foo) {
	}
}
