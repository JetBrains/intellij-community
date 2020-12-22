import java.util.List;

public class SuperBuilderWithGenerics2 {
	@lombok.experimental.SuperBuilder
	public static class Parent<A> {
		A field1;
		@lombok.Singular List<String> items;
	}
	
	@lombok.experimental.SuperBuilder(builderMethodName="builder2")
	public static class Child<A> extends Parent<String> {
		A field3;
	}
	
	public static void test() {
		Child<Integer> x = Child.<Integer>builder2().field3(1).field1("value").item("").build();
	}
}
