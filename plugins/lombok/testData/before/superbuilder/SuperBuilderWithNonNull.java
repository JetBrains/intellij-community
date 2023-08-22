public class SuperBuilderWithNonNull {
	@lombok.experimental.SuperBuilder
	public static class Parent {
		@lombok.NonNull
		@lombok.Builder.Default
		final String nonNullParentField = "default";
	}
	
	@lombok.experimental.SuperBuilder
	public static class Child extends Parent {
		@lombok.NonNull
		String nonNullChildField;
	}
	
	public static void test() {
		Child x = Child.builder().nonNullChildField("child").nonNullParentField("parent").build();
	}
}
