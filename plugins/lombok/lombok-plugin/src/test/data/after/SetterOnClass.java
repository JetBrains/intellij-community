class SetterOnClass1 {
	boolean isNone;
	boolean isPublic;
	@java.lang.SuppressWarnings("all")
	public void setPublic(final boolean isPublic) {
		this.isPublic = isPublic;
	}
}
class SetterOnClass2 {
	boolean isNone;
	boolean isProtected;
	boolean isPackage;
	@java.lang.SuppressWarnings("all")
	protected void setProtected(final boolean isProtected) {
		this.isProtected = isProtected;
	}
	@java.lang.SuppressWarnings("all")
	void setPackage(final boolean isPackage) {
		this.isPackage = isPackage;
	}
}
class SetterOnClass3 {
	boolean isNone;
	boolean isPackage;
	@java.lang.SuppressWarnings("all")
	void setPackage(final boolean isPackage) {
		this.isPackage = isPackage;
	}
}
class SetterOnClass4 {
	boolean isNone;
	boolean isPrivate;
	@java.lang.SuppressWarnings("all")
	private void setPrivate(final boolean isPrivate) {
		this.isPrivate = isPrivate;
	}
}
class SetterOnClass5 {
	boolean isNone;
	boolean isPublic;
	@java.lang.SuppressWarnings("all")
	public void setPublic(final boolean isPublic) {
		this.isPublic = isPublic;
	}
}
class SetterOnClass6 {
	String couldBeNull;
	@lombok.NonNull
	String nonNull;
	@java.lang.SuppressWarnings("all")
	public void setCouldBeNull(final String couldBeNull) {
		this.couldBeNull = couldBeNull;
	}
	@java.lang.SuppressWarnings("all")
	public void setNonNull(@lombok.NonNull final String nonNull) {
		if (nonNull == null) {
			throw new java.lang.NullPointerException("nonNull");
		}
		this.nonNull = nonNull;
	}
}