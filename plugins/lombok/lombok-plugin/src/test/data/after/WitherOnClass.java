class WitherOnClass1 {
	boolean isNone;
	boolean isPublic;
	WitherOnClass1(boolean isNone, boolean isPublic) {
	}
	@java.lang.SuppressWarnings("all")
	public WitherOnClass1 withPublic(final boolean isPublic) {
		return this.isPublic == isPublic ? this : new WitherOnClass1(this.isNone, isPublic);
	}
}
class WitherOnClass2 {
	boolean isNone;
	boolean isProtected;
	boolean isPackage;
	WitherOnClass2(boolean isNone, boolean isProtected, boolean isPackage) {
	}
	@java.lang.SuppressWarnings("all")
	protected WitherOnClass2 withProtected(final boolean isProtected) {
		return this.isProtected == isProtected ? this : new WitherOnClass2(this.isNone, isProtected, this.isPackage);
	}
	@java.lang.SuppressWarnings("all")
	WitherOnClass2 withPackage(final boolean isPackage) {
		return this.isPackage == isPackage ? this : new WitherOnClass2(this.isNone, this.isProtected, isPackage);
	}
}
class WitherOnClass3 {
	String couldBeNull;
	@lombok.NonNull
	String nonNull;
	WitherOnClass3(String couldBeNull, String nonNull) {
	}
	@java.lang.SuppressWarnings("all")
	public WitherOnClass3 withCouldBeNull(final String couldBeNull) {
		return this.couldBeNull == couldBeNull ? this : new WitherOnClass3(couldBeNull, this.nonNull);
	}
	@java.lang.SuppressWarnings("all")
	public WitherOnClass3 withNonNull(@lombok.NonNull final String nonNull) {
		if (nonNull == null) {
			throw new java.lang.NullPointerException("nonNull");
		}
		return this.nonNull == nonNull ? this : new WitherOnClass3(this.couldBeNull, nonNull);
	}
}
class WitherOnClass4 {
	final int fX = 10;
	final int fY;
	WitherOnClass4(int y) {
		this.fY = y;
	}
	@java.lang.SuppressWarnings("all")
	public WitherOnClass4 withY(final int fY) {
		return this.fY == fY ? this : new WitherOnClass4(fY);
	}
}
