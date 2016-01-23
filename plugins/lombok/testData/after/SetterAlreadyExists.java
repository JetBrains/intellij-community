class Setter1 {
	boolean foo;
	void setFoo(boolean foo) {
	}
}
class Setter2 {
	boolean foo;
	void setFoo(String foo) {
	}
}
class Setter3 {
	String foo;
	void setFoo(boolean foo) {
	}
}
class Setter4 {
	String foo;
	void setFoo(String foo) {
	}
}
class Setter5 {
	String foo;
	void setFoo() {
	}
	@java.lang.SuppressWarnings("all")
	public void setFoo(final String foo) {
		this.foo = foo;
	}
}
class Setter6 {
	String foo;
	void setFoo(String foo, int x) {
	}
	@java.lang.SuppressWarnings("all")
	public void setFoo(final String foo) {
		this.foo = foo;
	}
}
class Setter7 {
	String foo;
	void setFoo(String foo, Object... x) {
	}
}
class Setter8 {
	boolean isFoo;
	void setIsFoo(boolean foo) {
	}
}
class Setter9 {
	boolean isFoo;
	void setFoo(boolean foo) {
	}
}