class EqualsAndHashCode {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode)) return false;
		final EqualsAndHashCode other = (EqualsAndHashCode) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		if (this.x != other.x) return false;
		if (!java.util.Arrays.deepEquals(this.z, other.z)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof EqualsAndHashCode;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.x;
		result = result * PRIME + java.util.Arrays.deepHashCode(this.z);
		return result;
	}
}
