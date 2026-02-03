<error descr="@Builder.Default and @Singular cannot be mixed."><error descr="@Builder.Default requires an initializing expression (' = something;').">@lombok.experimental.SuperBuilder</error></error>
public class BuilderDefaultsWarnings {
	long x = System.currentTimeMillis();
	final int y = 5;
	@lombok.Builder.Default int z;
	@lombok.Builder.Default @lombok.Singular java.util.List<String> items;
}

class NoBuilderButHasDefaults {
	<error descr="@Builder.Default requires @Builder or @SuperBuilder on the class for it to mean anything.">@lombok.Builder.Default</error> private final long z = 5;

  @lombok.experimental.SuperBuilder
	static class SomeOtherClass {
	}
}
