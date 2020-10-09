public class SuperBuilderWithDefaults {
	@lombok.experimental.SuperBuilder
	public static class Parent<N extends Number> {
		@lombok.Builder.Default private long millis = System.currentTimeMillis();
		@lombok.Builder.Default private N numberField = null;
	}
	
	@lombok.experimental.SuperBuilder
	public static class Child extends Parent<Integer> {
		@lombok.Builder.Default private double doubleField = Math.PI;
	}
	
	public static void test() {
		Child x = Child.builder().doubleField(0.1).numberField(5).millis(1234567890L).build();
	}
}
