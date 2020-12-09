@lombok.AllArgsConstructor
class WitherAndAccessors {

	final int x = 10;

	int y = 20;

	@lombok.experimental.Accessors(fluent=true)
	@lombok.experimental.Wither final int z;
}
