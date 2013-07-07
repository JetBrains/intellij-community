class AccessorsFluent {
	private String fieldName = "";
	@java.lang.SuppressWarnings("all")
	public String fieldName() {
		return this.fieldName;
	}
	@java.lang.SuppressWarnings("all")
	public AccessorsFluent fieldName(final String fieldName) {
		this.fieldName = fieldName;
		return this;
	}
}
class AccessorsFluentOnClass {
	private String fieldName = "";
	private String otherFieldWithOverride = "";
	@java.lang.SuppressWarnings("all")
	public String fieldName() {
		return this.fieldName;
	}
	@java.lang.SuppressWarnings("all")
	public String getOtherFieldWithOverride() {
		return this.otherFieldWithOverride;
	}
	@java.lang.SuppressWarnings("all")
	public AccessorsFluentOnClass fieldName(final String fieldName) {
		this.fieldName = fieldName;
		return this;
	}
}
class AccessorsChain {
	private boolean isRunning;
	@java.lang.SuppressWarnings("all")
	public AccessorsChain setRunning(final boolean isRunning) {
		this.isRunning = isRunning;
		return this;
	}
}
class AccessorsPrefix {
	private String fieldName;
	private String fActualField;
	@java.lang.SuppressWarnings("all")
	public void setActualField(final String fActualField) {
		this.fActualField = fActualField;
	}
}
class AccessorsPrefix2 {
	private String fieldName;
	private String fActualField;
	@java.lang.SuppressWarnings("all")
	public void setFieldName(final String fieldName) {
		this.fieldName = fieldName;
	}
	@java.lang.SuppressWarnings("all")
	public void setActualField(final String fActualField) {
		this.fActualField = fActualField;
	}
}
class AccessorsPrefix3 {
	private String fName;
	private String getName() {
		return fName;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "AccessorsPrefix3(fName=" + this.getName() + ")";
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof AccessorsPrefix3)) return false;
		final AccessorsPrefix3 other = (AccessorsPrefix3)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		final java.lang.Object this$fName = this.getName();
		final java.lang.Object other$fName = other.getName();
		if (this$fName == null ? other$fName != null : !this$fName.equals(other$fName)) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof AccessorsPrefix3;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		final java.lang.Object $fName = this.getName();
		result = result * PRIME + ($fName == null ? 0 : $fName.hashCode());
		return result;
	}
}
class AccessorsFluentGenerics<T extends Number> {
	private String name;
	@java.lang.SuppressWarnings("all")
	public AccessorsFluentGenerics<T> name(final String name) {
		this.name = name;
		return this;
	}
}
class AccessorsFluentNoChaining {
	private String name;
	@java.lang.SuppressWarnings("all")
	public void name(final String name) {
		this.name = name;
	}
}
class AccessorsFluentStatic<T extends Number> {
	private static String name;
	@java.lang.SuppressWarnings("all")
	public static void name(final String name) {
		AccessorsFluentStatic.name = name;
	}
}