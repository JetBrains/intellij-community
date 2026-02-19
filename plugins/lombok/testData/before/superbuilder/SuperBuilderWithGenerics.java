import java.util.List;

public class SuperBuilderWithGenerics {
	@lombok.experimental.SuperBuilder
	public static class Parent<A> {
		A field1;
		@lombok.Singular List<String> items;
	}
	
	@lombok.experimental.SuperBuilder
	public static class Child<A> extends Parent<A> {
		double field3;
	}
	
	public static void test() {
		Child<Integer> x = Child.<Integer>builder().field3(0.0).field1(5).item("").build();
	}
}
