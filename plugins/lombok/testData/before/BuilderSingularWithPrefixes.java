import lombok.Singular;

@lombok.Builder
@lombok.experimental.Accessors(prefix = "_")
class BuilderSingularWithPrefixes {
	@Singular private java.util.List<String> _elems;
}
