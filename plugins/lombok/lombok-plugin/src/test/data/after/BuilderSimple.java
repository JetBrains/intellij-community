import java.util.List;
class BuilderSimple<T> {
	private final int noshow = 0;
	private final int yes;
	private List<T> also;
	private int $butNotMe;
	@java.lang.SuppressWarnings("all")
	private BuilderSimple(final int yes, final List<T> also) {
		this.yes = yes;
		this.also = also;
	}
	@java.lang.SuppressWarnings("all")
	public static class BuilderSimpleBuilder<T> {
		private int yes;
		private List<T> also;
		@java.lang.SuppressWarnings("all")
		BuilderSimpleBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSimpleBuilder<T> yes(final int yes) {
			this.yes = yes;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSimpleBuilder<T> also(final List<T> also) {
			this.also = also;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSimple<T> build() {
			return new BuilderSimple<T>(yes, also);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "BuilderSimple.BuilderSimpleBuilder(yes=" + this.yes + ", also=" + this.also + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	public static <T> BuilderSimpleBuilder<T> builder() {
		return new BuilderSimpleBuilder<T>();
	}
}
