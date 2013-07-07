import lombok.*;
import static lombok.AccessLevel.NONE;
class EqualsAndHashCodeWithSomeExistingMethods {
	int x;
	public int hashCode() {
		return 42;
	}
	@java.lang.SuppressWarnings("all")
	public EqualsAndHashCodeWithSomeExistingMethods() {
		
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "EqualsAndHashCodeWithSomeExistingMethods(x=" + this.x + ")";
	}
}
class EqualsAndHashCodeWithSomeExistingMethods2 {
	int x;
	public boolean canEqual(Object other) {
		return false;
	}
	@java.lang.SuppressWarnings("all")
	public EqualsAndHashCodeWithSomeExistingMethods2() {
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
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
	@java.lang.SuppressWarnings("all")
	public EqualsAndHashCodeWithAllExistingMethods() {
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "EqualsAndHashCodeWithAllExistingMethods(x=" + this.x + ")";
	}
}
class EqualsAndHashCodeWithNoExistingMethods {
	int x;
	@java.lang.SuppressWarnings("all")
	public EqualsAndHashCodeWithNoExistingMethods() {
		
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof EqualsAndHashCodeWithNoExistingMethods)) return false;
		final EqualsAndHashCodeWithNoExistingMethods other = (EqualsAndHashCodeWithNoExistingMethods)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.x != other.x) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof EqualsAndHashCodeWithNoExistingMethods;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + this.x;
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "EqualsAndHashCodeWithNoExistingMethods(x=" + this.x + ")";
	}
}
