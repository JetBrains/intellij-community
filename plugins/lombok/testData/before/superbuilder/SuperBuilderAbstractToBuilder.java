public class SuperBuilderAbstractToBuilder {
	@lombok.experimental.SuperBuilder(toBuilder = true)
	public static class Parent {
		int parentField;
	}
	
	@lombok.experimental.SuperBuilder(toBuilder = true)
	public abstract static class Child extends Parent {
		double childField;
	}
	
	@lombok.experimental.SuperBuilder(toBuilder = true)
	public static class GrandChild extends Child {
		String grandChildField;
	}
	
	public static void test() {
		GrandChild x = GrandChild.builder().grandChildField("").parentField(5).childField(2.5).build().toBuilder().build();
	}
}
