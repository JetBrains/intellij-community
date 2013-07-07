@lombok.EqualsAndHashCode
class EqualsAndHashCodeWithExistingMethods {
	int x;
	
	public int hashCode() {
		return 42;
	}
}

@lombok.EqualsAndHashCode
final class EqualsAndHashCodeWithExistingMethods2 {
	int x;
	
	public boolean equals(Object other) {
		return false;
	}
}

@lombok.EqualsAndHashCode(callSuper=true)
final class EqualsAndHashCodeWithExistingMethods3 extends EqualsAndHashCodeWithExistingMethods {
	int x;
	
	public boolean canEqual(Object other) {
		return true;
	}
}