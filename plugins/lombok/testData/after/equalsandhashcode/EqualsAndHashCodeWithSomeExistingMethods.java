class EqualsAndHashCodeWithSomeExistingMethods {
	int x;
	public int hashCode() {
		return 42;
	}
	@SuppressWarnings("all")
	public EqualsAndHashCodeWithSomeExistingMethods() {
		
	}
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "EqualsAndHashCodeWithSomeExistingMethods(x=" + this.x + ")";
	}
}

class EqualsAndHashCodeWithSomeExistingMethods2 {
	int x;
	protected boolean canEqual(Object other) {
		return false;
	}
	@SuppressWarnings("all")
	public EqualsAndHashCodeWithSomeExistingMethods2() {
	}
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeWithSomeExistingMethods2)) return false;
		final EqualsAndHashCodeWithSomeExistingMethods2 other = (EqualsAndHashCodeWithSomeExistingMethods2) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		return result;
	}
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "EqualsAndHashCodeWithSomeExistingMethods2(x=" + this.x + ")";
	}
}

class EqualsAndHashCodeWithAllExistingMethods {
	int x;
	public int hashCode() {
		return 42;
	}
	public boolean equals(Object other) {
		return false;
	}
	@SuppressWarnings("all")
	public EqualsAndHashCodeWithAllExistingMethods() {
	}
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "EqualsAndHashCodeWithAllExistingMethods(x=" + this.x + ")";
	}
}

class EqualsAndHashCodeWithNoExistingMethods {
	int x;
	@SuppressWarnings("all")
	public EqualsAndHashCodeWithNoExistingMethods() {
		
	}
	@Override
	@SuppressWarnings("all")
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeWithNoExistingMethods)) return false;
		final EqualsAndHashCodeWithNoExistingMethods other = (EqualsAndHashCodeWithNoExistingMethods) o;
		if (!other.canEqual((Object) this)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@SuppressWarnings("all")
	protected boolean canEqual(final Object other) {
		return other instanceof EqualsAndHashCodeWithNoExistingMethods;
	}
	@Override
	@SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		return result;
	}
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "EqualsAndHashCodeWithNoExistingMethods(x=" + this.x + ")";
	}
}
