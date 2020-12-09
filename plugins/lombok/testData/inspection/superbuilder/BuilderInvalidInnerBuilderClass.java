<error descr="Existing Builder must be an abstract static inner class.">@lombok.experimental.SuperBuilder</error>
class BuilderInvalidUse {
	private int something;

	public class BuilderInvalidUseBuilder {

	}
}
