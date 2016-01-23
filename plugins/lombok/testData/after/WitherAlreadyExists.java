class Wither1 {
	boolean foo;
	void withFoo(boolean foo) {
	}
	Wither1(boolean foo) {
	}
}
class Wither2 {
	boolean foo;
	void withFoo(String foo) {
	}
	Wither2(boolean foo) {
	}
}
class Wither3 {
	String foo;
	void withFoo(boolean foo) {
	}
	Wither3(String foo) {
	}
}
class Wither4 {
	String foo;
	void withFoo(String foo) {
	}
	Wither4(String foo) {
	}
}
class Wither5 {
	String foo;
	void withFoo() {
	}
	Wither5(String foo) {
	}
	@java.lang.SuppressWarnings("all")
	public Wither5 withFoo(final String foo) {
		return this.foo == foo ? this : new Wither5(foo);
	}
}
class Wither6 {
	String foo;
	void withFoo(String foo, int x) {
	}
	Wither6(String foo) {
	}
	@java.lang.SuppressWarnings("all")
	public Wither6 withFoo(final String foo) {
		return this.foo == foo ? this : new Wither6(foo);
	}
}
class Wither7 {
	String foo;
	void withFoo(String foo, Object... x) {
	}
	Wither7(String foo) {
	}
}
class Wither8 {
	boolean isFoo;
	void withIsFoo(boolean foo) {
	}
	Wither8(boolean foo) {
	}
}
class Wither9 {
	boolean isFoo;
	void withFoo(boolean foo) {
	}
	Wither9(boolean foo) {
	}
}