class GetterLazyBoolean {
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>> booleanValue = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>> otherBooleanValue = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>>();
	private static boolean calculateBoolean() {
		return true;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof GetterLazyBoolean)) return false;
		final GetterLazyBoolean other = (GetterLazyBoolean)o;
		if (!other.canEqual((java.lang.Object)this)) return false;
		if (this.isBooleanValue() != other.isBooleanValue()) return false;
		return true;
	}
	@java.lang.SuppressWarnings("all")
	public boolean canEqual(final java.lang.Object other) {
		return other instanceof GetterLazyBoolean;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = result * PRIME + (this.isBooleanValue() ? 1231 : 1237);
		return result;
	}
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "GetterLazyBoolean(booleanValue=" + this.isBooleanValue() + ")";
	}
	@java.lang.SuppressWarnings("all")
	public boolean isBooleanValue() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Boolean> value = this.booleanValue.get();
		if (value == null) {
			synchronized (this.booleanValue) {
				value = this.booleanValue.get();
				if (value == null) {
					final boolean actualValue = calculateBoolean();
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>(actualValue);
					this.booleanValue.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public boolean isOtherBooleanValue() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Boolean> value = this.otherBooleanValue.get();
		if (value == null) {
			synchronized (this.otherBooleanValue) {
				value = this.otherBooleanValue.get();
				if (value == null) {
					final boolean actualValue = !calculateBoolean();
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>(actualValue);
					this.otherBooleanValue.set(value);
				}
			}
		}
		return value.get();
	}
}
