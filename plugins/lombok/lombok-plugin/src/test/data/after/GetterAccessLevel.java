class GetterAccessLevel {
	boolean isNone;
	boolean isPrivate;
	boolean isPackage;
	boolean isProtected;
	boolean isPublic;
	String noneString;
	String privateString;
	String packageString;
	String protectedString;
	String publicString;
	String value;
	@java.lang.SuppressWarnings("all")
	private boolean isPrivate() {
		return this.isPrivate;
	}
	@java.lang.SuppressWarnings("all")
	boolean isPackage() {
		return this.isPackage;
	}
	@java.lang.SuppressWarnings("all")
	protected boolean isProtected() {
		return this.isProtected;
	}
	@java.lang.SuppressWarnings("all")
	public boolean isPublic() {
		return this.isPublic;
	}
	@java.lang.SuppressWarnings("all")
	private String getPrivateString() {
		return this.privateString;
	}
	@java.lang.SuppressWarnings("all")
	String getPackageString() {
		return this.packageString;
	}
	@java.lang.SuppressWarnings("all")
	protected String getProtectedString() {
		return this.protectedString;
	}
	@java.lang.SuppressWarnings("all")
	public String getPublicString() {
		return this.publicString;
	}
	@java.lang.SuppressWarnings("all")
	public String getValue() {
		return this.value;
	}
}