public class BuilderWithFieldAccessors {
	private final int pUpper;
	private int _foo;
	private String mBar;

	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderWithFieldAccessors(final int upper, final int foo, final String bar) {
		this.pUpper = upper;
		this._foo = foo;
		this.mBar = bar;
	}

	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderWithFieldAccessorsBuilder {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int upper;

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int foo;

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private String bar;

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderWithFieldAccessorsBuilder() {
		}

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithFieldAccessorsBuilder upper(final int upper) {
			this.upper = upper;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithFieldAccessorsBuilder foo(final int foo) {
			this.foo = foo;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithFieldAccessorsBuilder bar(final String bar) {
			this.bar = bar;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderWithFieldAccessors build() {
			return new BuilderWithFieldAccessors(upper, foo, bar);
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderWithFieldAccessors.BuilderWithFieldAccessorsBuilder(upper=" + this.upper + ", foo=" + this.foo + ", bar=" + this.bar + ")";
		}
	}

	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderWithFieldAccessorsBuilder builder() {
		return new BuilderWithFieldAccessorsBuilder();
	}
}
