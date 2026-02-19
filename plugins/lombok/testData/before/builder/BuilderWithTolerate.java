import lombok.Builder;
import lombok.experimental.Tolerate;

@Builder
public class BuilderWithTolerate {
	private final int value;

	public static void main(String[] args) {
		BuilderWithTolerate.builder().value("42").build();
	}

	public static class BuilderWithTolerateBuilder {
		@Tolerate
		public BuilderWithTolerateBuilder value(String s) {
			return this.value(Integer.parseInt(s));
		}
	}
}
