@lombok.With
class WithOnClass1 {
	@lombok.With(lombok.AccessLevel.NONE)
	boolean isNone;
	
	boolean isPublic;
	
	WithOnClass1(boolean isNone, boolean isPublic) {
	}
}

@lombok.With(lombok.AccessLevel.PROTECTED)
class WithOnClass2 {
	@lombok.With(lombok.AccessLevel.NONE)
	boolean isNone;
	
	boolean isProtected;
	
	@lombok.With(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
	
	WithOnClass2(boolean isNone, boolean isProtected, boolean isPackage) {
	}
}

@lombok.With
class WithOnClass3 {
	String couldBeNull;
	
	@lombok.NonNull String nonNull;
	
	WithOnClass3(String couldBeNull, String nonNull) {
	}
}

@lombok.With @lombok.experimental.Accessors(prefix="f")
class WithOnClass4 {
	final int fX = 10;
	
	final int fY;
	
	WithOnClass4(int y) {
		this.fY = y;
	}
}
