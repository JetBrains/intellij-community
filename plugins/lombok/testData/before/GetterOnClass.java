@lombok.Getter
class GetterOnClass1 {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPublic;
}
@lombok.Getter(lombok.AccessLevel.PROTECTED)
class GetterOnClass2 {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isProtected;
	@lombok.Getter(lombok.AccessLevel.PACKAGE)
	boolean isPackage;
}
@lombok.Getter(lombok.AccessLevel.PACKAGE)
class GetterOnClass3 {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPackage;
}
@lombok.Getter(lombok.AccessLevel.PRIVATE)
class GetterOnClass4 {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPrivate;
}
@lombok.Getter(lombok.AccessLevel.PUBLIC)
class GetterOnClass5 {
	@lombok.Getter(lombok.AccessLevel.NONE)
	boolean isNone;
	boolean isPublic;
}
@lombok.Getter
class GetterOnClass6 {
	String couldBeNull;
	@lombok.NonNull String nonNull;
}