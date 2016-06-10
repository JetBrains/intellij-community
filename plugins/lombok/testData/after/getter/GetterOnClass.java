class GetterOnClass1 {
	boolean isNone;
	boolean isPublic;
	@java.lang.SuppressWarnings("all")
	public boolean isPublic() {
		return this.isPublic;
	}
}
class GetterOnClass2 {
	boolean isNone;
	boolean isProtected;
	boolean isPackage;
	@java.lang.SuppressWarnings("all")
	protected boolean isProtected() {
		return this.isProtected;
	}
	@java.lang.SuppressWarnings("all")
	boolean isPackage() {
		return this.isPackage;
	}
}
class GetterOnClass3 {
	boolean isNone;
	boolean isPackage;
	@java.lang.SuppressWarnings("all")
	boolean isPackage() {
		return this.isPackage;
	}
}
class GetterOnClass4 {
	boolean isNone;
	boolean isPrivate;
	@java.lang.SuppressWarnings("all")
	private boolean isPrivate() {
		return this.isPrivate;
	}
}
class GetterOnClass5 {
	boolean isNone;
	boolean isPublic;
	@java.lang.SuppressWarnings("all")
	public boolean isPublic() {
		return this.isPublic;
	}
}
class GetterOnClass6 {
	String couldBeNull;
	@lombok.NonNull
	String nonNull;
	@java.lang.SuppressWarnings("all")
	public String getCouldBeNull() {
		return this.couldBeNull;
	}
	@lombok.NonNull
	@java.lang.SuppressWarnings("all")
	public String getNonNull() {
		return this.nonNull;
	}
}