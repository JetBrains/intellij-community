final class ValueWithJavaBeansSpecCapitalization {
	private final int aField;
	public ValueWithJavaBeansSpecCapitalization(final int aField) {
		this.aField = aField;
	}
	public int getaField() {
		return this.aField;
	}

	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof ValueWithJavaBeansSpecCapitalization)) return false;
		final ValueWithJavaBeansSpecCapitalization other = (ValueWithJavaBeansSpecCapitalization) o;
		if (this.getaField() != other.getaField()) return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.getaField();
		return result;
	}

	public String toString() {
		return "ValueWithJavaBeansSpecCapitalization(aField=" + this.getaField() + ")";
	}
}