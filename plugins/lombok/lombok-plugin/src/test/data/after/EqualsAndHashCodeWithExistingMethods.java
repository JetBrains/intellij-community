class EqualsAndHashCodeWithExistingMethods {
	int x;
	public int hashCode() {
		return 42;
	}
}
final class EqualsAndHashCodeWithExistingMethods2 {
	int x;
	public boolean equals(Object other) {
		return false;
	}
}
final class EqualsAndHashCodeWithExistingMethods3 extends EqualsAndHashCodeWithExistingMethods {
	int x;
	protected boolean canEqual(Object other) {
		return true;
	}
}