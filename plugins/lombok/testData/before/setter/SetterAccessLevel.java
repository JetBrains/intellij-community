class SetterAccessLevel {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	
	@lombok.Setter(lombok.AccessLevel.PRIVATE)
	boolean isPrivate;
	
	@lombok.Setter(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
	
	@lombok.Setter(lombok.AccessLevel.PROTECTED)
	boolean isProtected;
	
	@lombok.Setter(lombok.AccessLevel.PUBLIC)
	boolean isPublic;
	
	@lombok.Setter(value=lombok.AccessLevel.PUBLIC)
	boolean value;
}
