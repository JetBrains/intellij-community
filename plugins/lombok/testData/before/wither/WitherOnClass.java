@lombok.experimental.Wither
class WitherOnClass1 {
	@lombok.experimental.Wither(lombok.AccessLevel.NONE)
	boolean isNone;
	
	boolean isPublic;
	
	WitherOnClass1(boolean isNone, boolean isPublic) {
	}
}

@lombok.experimental.Wither(lombok.AccessLevel.PROTECTED)
class WitherOnClass2 {
	@lombok.experimental.Wither(lombok.AccessLevel.NONE)
	boolean isNone;
	
	boolean isProtected;
	
	@lombok.experimental.Wither(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
	
	WitherOnClass2(boolean isNone, boolean isProtected, boolean isPackage) {
	}
}

@lombok.experimental.Wither
class WitherOnClass3 {
	String couldBeNull;
	
	@lombok.NonNull String nonNull;
	
	WitherOnClass3(String couldBeNull, String nonNull) {
	}
}

@lombok.experimental.Wither @lombok.experimental.Accessors(prefix="f")
class WitherOnClass4 {
	final int fX = 10;
	
	final int fY;
	
	WitherOnClass4(int y) {
		this.fY = y;
	}
}
