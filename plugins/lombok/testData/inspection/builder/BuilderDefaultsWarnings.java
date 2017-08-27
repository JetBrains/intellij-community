@lombok.Builder
public class BuilderDefaultsWarnings {
	long x = System.currentTimeMillis();
	final int y = 5;
	@lombok.Builder.Default int z;
	@lombok.Builder.Default @lombok.Singular java.util.List<String> items;
}

class NoBuilderButHasDefaults {
	@lombok.Builder.Default private final long z = 5;

	@lombok.Builder
	public NoBuilderButHasDefaults() {
	}
}
