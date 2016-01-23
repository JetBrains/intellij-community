class WitherPlain {
	int i;
	final int foo;
	WitherPlain(int i, int foo) {
		this.i = i;
		this.foo = foo;
	}
	@java.lang.SuppressWarnings("all")
	public WitherPlain withI(final int i) {
		return this.i == i ? this : new WitherPlain(i, this.foo);
	}
	@java.lang.SuppressWarnings("all")
	public WitherPlain withFoo(final int foo) {
		return this.foo == foo ? this : new WitherPlain(this.i, foo);
	}
}
