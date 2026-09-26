//skip compare content
<error descr="Lombok's annotations are not allowed on builder class.">@lombok.experimental.SuperBuilder</error>
class BuilderInvalidUse {
	private int something;

	@lombok.Getter @lombok.Setter @lombok.experimental.FieldDefaults(makeFinal = true) @lombok.experimental.Wither @lombok.Data @lombok.ToString @lombok.EqualsAndHashCode
	@lombok.AllArgsConstructor
	public static class BuilderInvalidUseBuilder {

	}
}

<error descr="Lombok's annotations are not allowed on builder class.">@lombok.experimental.SuperBuilder</error>
class AlsoInvalid {
	@lombok.Value
	public static class AlsoInvalidBuilder {

	}
}
