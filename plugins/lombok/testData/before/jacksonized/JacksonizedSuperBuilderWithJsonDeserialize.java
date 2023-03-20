//version 8: Jackson deps are at least Java7+.
@lombok.extern.jackson.Jacksonized
@lombok.experimental.SuperBuilder
@com.fasterxml.jackson.databind.annotation.JsonDeserialize
public class JacksonizedSuperBuilderWithJsonDeserialize {
	int field1;
}
