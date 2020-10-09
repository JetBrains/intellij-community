@lombok.EqualsAndHashCode(of="booleanValue")
@lombok.ToString(of="booleanValue")
class GetterLazyBoolean {
	@lombok.Getter(lazy=true)
	private final boolean booleanValue = calculateBoolean();

	@lombok.Getter(lazy=true)
	private final boolean otherBooleanValue = !calculateBoolean();
	
	private static boolean calculateBoolean() {
		return true;
	}
}
