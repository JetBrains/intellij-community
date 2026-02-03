import lombok.experimental.Wither;
class WitherPlain {
	@lombok.experimental.Wither int i;
	@Wither final int foo;
	
	WitherPlain(int i, int foo) {
		this.i = i;
		this.foo = foo;
	}
}
