final class Value1 {
	private final int x;
	private final String name;
	@java.beans.ConstructorProperties({"x", "name"})
	@java.lang.SuppressWarnings("all")
	public Value1(final int x, final String name) {
		this.x = x;
		this.name = name;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Value1)) return false;
		final Value1 other = (Value1)o;
		if (this.getX() != other.getX()) return false;
		final java.lang.Object this$name = this.getName();
		final java.lang.Object other$name = other.getName();
		if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
		return true;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.getX();
		final java.lang.Object $name = this.getName();
		result = result * PRIME + ($name == null ? 0 : $name.hashCode());
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Value1(x=" + this.getX() + ", name=" + this.getName() + ")";
	}
}
class Value2 {
	public final int x;
	private final String name;
	@java.beans.ConstructorProperties({"x", "name"})
	@java.lang.SuppressWarnings("all")
	public Value2(final int x, final String name) {
		this.x = x;
		this.name = name;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Value2)) return false;
		final Value2 other = (Value2)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.getX() != other.getX()) return false;
		final java.lang.Object this$name = this.getName();
		final java.lang.Object other$name = other.getName();
		if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof Value2;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.getX();
		final java.lang.Object $name = this.getName();
		result = result * PRIME + ($name == null ? 0 : $name.hashCode());
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Value2(x=" + this.getX() + ", name=" + this.getName() + ")";
	}
}
final class Value3 {
	private int x;
	private final int y;
	@java.beans.ConstructorProperties({"x", "y"})
	@java.lang.SuppressWarnings("all")
	public Value3(final int x, final int y) {
		this.x = x;
		this.y = y;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public int getY() {
		return this.y;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Value3)) return false;
		final Value3 other = (Value3)o;
		if (this.getX() != other.getX()) return false;
		if (this.getY() != other.getY()) return false;
		return true;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.getX();
		result = result * PRIME + this.getY();
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Value3(x=" + this.getX() + ", y=" + this.getY() + ")";
	}
}
