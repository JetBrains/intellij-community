@lombok.Setter
class SetterOnClass1 {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPublic;
}

@lombok.Setter(lombok.AccessLevel.PROTECTED)
class SetterOnClass2 {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isProtected;
	@lombok.Setter(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
}

@lombok.Setter(lombok.AccessLevel.PACKAGE)
class SetterOnClass3 {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPackage;
}

@lombok.Setter(lombok.AccessLevel.PRIVATE)
class SetterOnClass4 {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPrivate;
}

@lombok.Setter(lombok.AccessLevel.PUBLIC)
class SetterOnClass5 {
	@lombok.Setter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPublic;
}

@lombok.Setter
class SetterOnClass6 {
	String couldBeNull;
	@lombok.NonNull String nonNull;
}