class DataWithGetter {
	private int x;
	private int y;
	private final String z;
	@java.beans.ConstructorProperties({"z"})
	@java.lang.SuppressWarnings("all")
	public DataWithGetter(final String z) {
		this.z = z;
	}
	@java.lang.SuppressWarnings("all")
	public void setX(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public void setY(final int y) {
		this.y = y;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof DataWithGetter)) return false;
		final DataWithGetter other = (DataWithGetter)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.getX() != other.getX()) return false;
		if (this.getY() != other.getY()) return false;
		final java.lang.Object this$z = this.getZ();
		final java.lang.Object other$z = other.getZ();
		if (this$z == null ? other$z != null : !this$z.equals(other$z)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof DataWithGetter;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.getX();
		result = result * PRIME + this.getY();
		final java.lang.Object $z = this.getZ();
		result = result * PRIME + ($z == null ? 0 : $z.hashCode());
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "DataWithGetter(x=" + this.getX() + ", y=" + this.getY() + ", z=" + this.getZ() + ")";
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public int getY() {
		return this.y;
	}
	@java.lang.SuppressWarnings("all")
	public String getZ() {
		return this.z;
	}
}