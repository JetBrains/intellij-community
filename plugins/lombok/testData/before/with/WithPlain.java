import lombok.With;

class WithPlain {
	@lombok.With int i;
	@With
  final int foo;

	WithPlain(int i, int foo) {
		this.i = i;
		this.foo = foo;
	}
}
