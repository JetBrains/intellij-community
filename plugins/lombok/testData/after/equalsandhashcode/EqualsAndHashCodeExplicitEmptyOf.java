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
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof EqualsAndHashCode;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}

final class EqualsAndHashCode2 {
	int x;
	long y;
	float f;
	double d;
	boolean b;

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCode2)) return false;
		final EqualsAndHashCode2 other = (EqualsAndHashCode2) o;
		return true;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
}
