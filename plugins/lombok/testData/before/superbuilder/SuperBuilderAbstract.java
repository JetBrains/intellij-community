public class SuperBuilderAbstract {
	@lombok.experimental.SuperBuilder
	public static class Parent {
		int parentField;
	}
	
	@lombok.experimental.SuperBuilder
	public abstract static class Child extends Parent {
		double childField;
	}
	
	@lombok.experimental.SuperBuilder
	public static class GrandChild extends Child {
		String grandChildField;
	}
	
	public static void test() {
		GrandChild x = GrandChild.builder().grandChildField("").parentField(5).childField(2.5).build();
	}
}
