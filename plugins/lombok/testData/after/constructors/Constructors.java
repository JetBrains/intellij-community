class RequiredArgsConstructor1 {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	public RequiredArgsConstructor1(final int x) {
		this.x = x;
	}
}
class RequiredArgsConstructorAccess {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@java.lang.SuppressWarnings("all")
	protected RequiredArgsConstructorAccess(final int x) {
		this.x = x;
	}
}
class RequiredArgsConstructorStaticName {
	final int x;
	String name;
	@java.lang.SuppressWarnings("all")
	private RequiredArgsConstructorStaticName(final int x) {
		this.x = x;
	}
	@java.lang.SuppressWarnings("all")
	public static RequiredArgsConstructorStaticName staticname(final int x) {
		return new RequiredArgsConstructorStaticName(x);
	}
}
class RequiredArgsConstructorWithAnnotations {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x"})
	@Deprecated
	@java.lang.SuppressWarnings("all")
	public RequiredArgsConstructorWithAnnotations(final int x) {
		this.x = x;
	}
}
class AllArgsConstructor1 {
	final int x;
	String name;
	@java.beans.ConstructorProperties({"x", "name"})
	@java.lang.SuppressWarnings("all")
	public AllArgsConstructor1(final int x, final String name) {
		this.x = x;
		this.name = name;
	}
}
class NoArgsConstructor1 {
	int x;
	String name;
	@java.lang.SuppressWarnings("all")
	public NoArgsConstructor1() {
	}
}
class RequiredArgsConstructorStaticNameGenerics<T extends Number> {
	final T x;
	String name;
	
	@java.lang.SuppressWarnings("all")
	private RequiredArgsConstructorStaticNameGenerics(final T x) {
		this.x = x;
	}
	
	@java.lang.SuppressWarnings("all")
	public static <T extends Number> RequiredArgsConstructorStaticNameGenerics<T> of(final T x) {
		return new RequiredArgsConstructorStaticNameGenerics<T>(x);
	}
}
class RequiredArgsConstructorStaticNameGenerics2<T extends Number> {
	final Class<T> x;
	String name;
	
	@java.lang.SuppressWarnings("all")
	private RequiredArgsConstructorStaticNameGenerics2(final Class<T> x) {
		this.x = x;
	}
	
	@java.lang.SuppressWarnings("all")
	public static <T extends Number> RequiredArgsConstructorStaticNameGenerics2<T> of(final Class<T> x) {
		return new RequiredArgsConstructorStaticNameGenerics2<T>(x);
	}
}