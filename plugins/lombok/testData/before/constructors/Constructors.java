@lombok.RequiredArgsConstructor class RequiredArgsConstructor1 {
	final int x;
	String name;
}
@lombok.RequiredArgsConstructor(access=lombok.AccessLevel.PROTECTED) class RequiredArgsConstructorAccess {
	final int x;
	String name;
}
@lombok.RequiredArgsConstructor(staticName="staticname") class RequiredArgsConstructorStaticName {
	final int x;
	String name;
}
@lombok.RequiredArgsConstructor(onConstructor=@_(@Deprecated)) class RequiredArgsConstructorWithAnnotations {
	final int x;
	String name;
}
@lombok.AllArgsConstructor class AllArgsConstructor1 {
	final int x;
	String name;
}
@lombok.NoArgsConstructor class NoArgsConstructor1 {
	int x;
	String name;
}
@lombok.RequiredArgsConstructor(staticName="of") class RequiredArgsConstructorStaticNameGenerics<T extends Number> {
	final T x;
	String name;
}
@lombok.RequiredArgsConstructor(staticName="of") class RequiredArgsConstructorStaticNameGenerics2<T extends Number> {
	final Class<T> x;
	String name;
}
