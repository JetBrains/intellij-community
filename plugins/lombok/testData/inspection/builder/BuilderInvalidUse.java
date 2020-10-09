//skip compare content
<error descr="Lombok annotations are not allowed on builder class.">@lombok.Builder</error>
class BuilderInvalidUse {
	private int something;

	@lombok.Getter @lombok.Setter @lombok.experimental.FieldDefaults(makeFinal = true) @lombok.experimental.Wither @lombok.Data @lombok.ToString @lombok.EqualsAndHashCode
	@lombok.AllArgsConstructor
	public static class BuilderInvalidUseBuilder {

	}
}

<error descr="Lombok annotations are not allowed on builder class.">@lombok.Builder</error>
class AlsoInvalid {
	@lombok.Value
	public static class AlsoInvalidBuilder {

	}
}
