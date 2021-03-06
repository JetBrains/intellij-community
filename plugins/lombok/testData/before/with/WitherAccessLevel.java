import lombok.AccessLevel;

class WitherAccessLevel {
	@lombok.experimental.Wither(lombok.AccessLevel.NONE)
	boolean isNone;
	
	@lombok.experimental.Wither(AccessLevel.PRIVATE)
	boolean isPrivate;
	
	@lombok.experimental.Wither(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
	
	@lombok.experimental.Wither(AccessLevel.PROTECTED)
	boolean isProtected;
	
	@lombok.experimental.Wither(lombok.AccessLevel.PUBLIC)
	boolean isPublic;
	
	@lombok.experimental.Wither(value=lombok.AccessLevel.PUBLIC)
	boolean value;
	
	WitherAccessLevel(boolean isNone, boolean isPrivate, boolean isPackage, boolean isProtected, boolean isPublic, boolean value) {
	}
}
