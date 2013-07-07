class WitherAccessLevel {
	boolean isNone;
	boolean isPrivate;
	boolean isPackage;
	boolean isProtected;
	boolean isPublic;
	boolean value;
	WitherAccessLevel(boolean isNone, boolean isPrivate, boolean isPackage, boolean isProtected, boolean isPublic, boolean value) {
	}
	@java.lang.SuppressWarnings("all")
	private WitherAccessLevel withPrivate(final boolean isPrivate) {
		return this.isPrivate == isPrivate ? this : new WitherAccessLevel(this.isNone, isPrivate, this.isPackage, this.isProtected, this.isPublic, this.value);
	}
	@java.lang.SuppressWarnings("all")
	WitherAccessLevel withPackage(final boolean isPackage) {
		return this.isPackage == isPackage ? this : new WitherAccessLevel(this.isNone, this.isPrivate, isPackage, this.isProtected, this.isPublic, this.value);
	}
	@java.lang.SuppressWarnings("all")
	protected WitherAccessLevel withProtected(final boolean isProtected) {
		return this.isProtected == isProtected ? this : new WitherAccessLevel(this.isNone, this.isPrivate, this.isPackage, isProtected, this.isPublic, this.value);
	}
	@java.lang.SuppressWarnings("all")
	public WitherAccessLevel withPublic(final boolean isPublic) {
		return this.isPublic == isPublic ? this : new WitherAccessLevel(this.isNone, this.isPrivate, this.isPackage, this.isProtected, isPublic, this.value);
	}
	@java.lang.SuppressWarnings("all")
	public WitherAccessLevel withValue(final boolean value) {
		return this.value == value ? this : new WitherAccessLevel(this.isNone, this.isPrivate, this.isPackage, this.isProtected, this.isPublic, value);
	}
}