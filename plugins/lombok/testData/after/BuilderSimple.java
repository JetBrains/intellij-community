import java.util.List;
class BuilderSimple<T> {
	private final int noshow = 0;
	private final int yes;
	private List<T> also;
	private int $butNotMe;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSimple(final int yes, final List<T> also) {
		this.yes = yes;
		this.also = also;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSimpleBuilder<T> {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int yes;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private List<T> also;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSimpleBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSimpleBuilder<T> yes(final int yes) {
			this.yes = yes;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSimpleBuilder<T> also(final List<T> also) {
			this.also = also;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSimple<T> build() {
			return new BuilderSimple<T>(yes, also);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSimple.BuilderSimpleBuilder(yes=" + this.yes + ", also=" + this.also + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> BuilderSimpleBuilder<T> builder() {
		return new BuilderSimpleBuilder<T>();
	}
}
