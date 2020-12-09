@lombok.Builder(setterPrefix = "set") @lombok.experimental.Accessors(prefix={"p", "_"})
class BuilderWithAccessorsWithSetterPrefix {
	private final int plower;
	private final int pUpper;
	private int _foo;
	private int __bar;
}
