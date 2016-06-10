class BuilderWithAccessors {
	private final int plower;
	private final int pUpper;
	private int _foo;
	private int __bar;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderWithAccessors(final int plower, final int upper, final int foo, final int _bar) {
		this.plower = plower;
		this.pUpper = upper;
		this._foo = foo;
		this.__bar = _bar;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderWithAccessorsBuilder {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int plower;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int upper;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int foo;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int _bar;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderWithAccessorsBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithAccessorsBuilder plower(final int plower) {
			this.plower = plower;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithAccessorsBuilder upper(final int upper) {
			this.upper = upper;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithAccessorsBuilder foo(final int foo) {
			this.foo = foo;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithAccessorsBuilder _bar(final int _bar) {
			this._bar = _bar;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithAccessors build() {
			return new BuilderWithAccessors(plower, upper, foo, _bar);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderWithAccessors.BuilderWithAccessorsBuilder(plower=" + this.plower + ", upper=" + this.upper + ", foo=" + this.foo + ", _bar=" + this._bar + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderWithAccessorsBuilder builder() {
		return new BuilderWithAccessorsBuilder();
	}
}