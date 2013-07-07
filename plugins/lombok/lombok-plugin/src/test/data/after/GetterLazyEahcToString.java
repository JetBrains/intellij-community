class GetterLazyEahcToString {
	
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<String>> value = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<String>>();
	private final String value2 = "";
	
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof GetterLazyEahcToString)) return false;
		final GetterLazyEahcToString other = (GetterLazyEahcToString)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		final java.lang.Object this$value = this.getValue();
		final java.lang.Object other$value = other.getValue();
		if (this$value == null ? other$value != null : !this$value.equals(other$value)) return false;
		final java.lang.Object this$value2 = this.value2;
		final java.lang.Object other$value2 = other.value2;
		if (this$value2 == null ? other$value2 != null : !this$value2.equals(other$value2)) return false;
		return true;
	}
	
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof GetterLazyEahcToString;
	}
	
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		final java.lang.Object $value = this.getValue();
		result = result * PRIME + ($value == null ? 0 : $value.hashCode());
		final java.lang.Object $value2 = this.value2;
		result = result * PRIME + ($value2 == null ? 0 : $value2.hashCode());
		return result;
	}
	
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "GetterLazyEahcToString(value=" + this.getValue() + ", value2=" + this.value2 + ")";
	}
	
	@java.lang.SuppressWarnings("all")
	public String getValue() {
		java.util.concurrent.atomic.AtomicReference<String> value = this.value.get();
		if (value == null) {
			synchronized (this.value) {
				value = this.value.get();
				if (value == null) {
					final String actualValue = "";
					value = new java.util.concurrent.atomic.AtomicReference<String>(actualValue);
					this.value.set(value);
				}
			}
		}
		return value.get();
	}
	
	@java.lang.SuppressWarnings("all")
	public String getValue2() {
		return this.value2;
	}
}