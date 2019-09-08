import lombok.experimental.Tolerate;

public class BuilderWithTolerate {
	private final int value;
	public static void main(String[] args) {
		BuilderWithTolerate.builder().value("42").build();
	}
	public static class BuilderWithTolerateBuilder {
		@SuppressWarnings("all")
		private int value;
		@Tolerate
		public BuilderWithTolerateBuilder value(String s) {
			return this.value(Integer.parseInt(s));
		}
		@SuppressWarnings("all")
		BuilderWithTolerateBuilder() {
		}
		@SuppressWarnings("all")
		public BuilderWithTolerateBuilder value(final int value) {
			this.value = value;
			return this;
		}
		@SuppressWarnings("all")
		public BuilderWithTolerate build() {
			return new BuilderWithTolerate(value);
		}
		@Override
		@SuppressWarnings("all")
		public String toString() {
			return "BuilderWithTolerate.BuilderWithTolerateBuilder(value=" + this.value + ")";
		}
	}
	@SuppressWarnings("all")
  BuilderWithTolerate(final int value) {
		this.value = value;
	}
	@SuppressWarnings("all")
	public static BuilderWithTolerateBuilder builder() {
		return new BuilderWithTolerateBuilder();
	}
}
