class DataIgnore {
	final int x;
	String $name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	public DataIgnore(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof DataIgnore)) return false;
		final DataIgnore other = (DataIgnore)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.getX() != other.getX()) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof DataIgnore;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.getX();
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "DataIgnore(x=" + this.getX() + ")";
	}
}