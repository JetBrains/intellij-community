class SetterAccessLevel {
	boolean isNone;
	boolean isPrivate;
	boolean isPackage;
	boolean isProtected;
	boolean isPublic;
	boolean value;
	@java.lang.SuppressWarnings("all")
	private void setPrivate(final boolean isPrivate) {
		this.isPrivate = isPrivate;
	}
	@java.lang.SuppressWarnings("all")
	void setPackage(final boolean isPackage) {
		this.isPackage = isPackage;
	}
	@java.lang.SuppressWarnings("all")
	protected void setProtected(final boolean isProtected) {
		this.isProtected = isProtected;
	}
	@java.lang.SuppressWarnings("all")
	public void setPublic(final boolean isPublic) {
		this.isPublic = isPublic;
	}
	@java.lang.SuppressWarnings("all")
	public void setValue(final boolean value) {
		this.value = value;
	}
}