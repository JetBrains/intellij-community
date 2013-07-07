class Data1 {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	public Data1(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
	@java.lang.SuppressWarnings("all")
	public void setName(final String name) {
		this.name = name;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data1)) return false;
		final Data1 other = (Data1)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.getX() != other.getX()) return false;
		final java.lang.Object this$name = this.getName();
		final java.lang.Object other$name = other.getName();
		if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof Data1;
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
		return "Data1(x=" + this.getX() + ", name=" + this.getName() + ")";
	}
}
class Data2 {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	public Data2(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
	@java.lang.SuppressWarnings("all")
	public void setName(final String name) {
		this.name = name;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data2)) return false;
		final Data2 other = (Data2)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.getX() != other.getX()) return false;
		final java.lang.Object this$name = this.getName();
		final java.lang.Object other$name = other.getName();
		if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof Data2;
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
		return "Data2(x=" + this.getX() + ", name=" + this.getName() + ")";
	}
}
final class Data3 {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	public Data3(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
	@java.lang.SuppressWarnings("all")
	public void setName(final String name) {
		this.name = name;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data3)) return false;
		final Data3 other = (Data3)o;
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
		return "Data3(x=" + this.getX() + ", name=" + this.getName() + ")";
	}
}
final class Data4 extends java.util.Timer {
	int x;
	Data4() {
	}
	@java.lang.SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
	@java.lang.SuppressWarnings("all")
	public void setX(final int x) {
		this.x = x;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Data4(x=" + this.getX() + ")";
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data4)) return false;
		final Data4 other = (Data4)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (!super.equals(o)) return false;
		if (this.getX() != other.getX()) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof Data4;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + super.hashCode();
		result = result * PRIME + this.getX();
		return result;
	}
}
class Data5 {
	@java.lang.SuppressWarnings("all")
	public Data5() {
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data5)) return false;
		final Data5 other = (Data5)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof Data5;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Data5()";
	}
}
final class Data6 {
	@java.lang.SuppressWarnings("all")
	public Data6() {
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Data6)) return false;
		return true;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		int result = 1;
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Data6()";
	}
}