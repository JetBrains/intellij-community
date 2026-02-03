class GetterAccessLevel {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	@lombok.Getter(lombok.AccessLevel.PRIVATE)
	boolean isPrivate;
	@lombok.Getter(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
	@lombok.Getter(lombok.AccessLevel.PROTECTED)
	boolean isProtected;
	@lombok.Getter(lombok.AccessLevel.PUBLIC)
	boolean isPublic;
	@lombok.Getter(lombok.AccessLevel.NONE)
	String noneString;
	@lombok.Getter(lombok.AccessLevel.PRIVATE)
	String privateString;
	@lombok.Getter(lombok.AccessLevel.PACKAGE)
	String packageString;
	@lombok.Getter(lombok.AccessLevel.PROTECTED)
	String protectedString;
	@lombok.Getter(lombok.AccessLevel.PUBLIC)
	String publicString;
	@lombok.Getter(value=lombok.AccessLevel.PUBLIC)
	String value;
}
