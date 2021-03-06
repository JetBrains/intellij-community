@lombok.Builder @lombok.experimental.Accessors(prefix={"p", "_"})
class BuilderWithAccessors {
	private final int plower;
	private final int pUpper;
	private int _foo;
	private int __bar;
}
