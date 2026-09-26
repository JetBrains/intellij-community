import java.util.List;

public class SuperBuilderBasicToBuilder {
	@lombok.experimental.SuperBuilder(toBuilder=true)
	public static class Parent {
		private int field1;
		@lombok.Builder.ObtainVia(field="field1")
		int obtainViaField;
		@lombok.Builder.ObtainVia(method="method")
		int obtainViaMethod;
		@lombok.Builder.ObtainVia(method = "staticMethod", isStatic = true)
		String obtainViaStaticMethod;
		@lombok.Singular List<String> items;
		
		private int method() {
			return 2;
		}

		private static String staticMethod(Parent instance) {
			return "staticMethod";
		}
	}
	
	@lombok.experimental.SuperBuilder(toBuilder=true)
	public static class Child extends Parent {
		private double field3;
	}
	
	public static void test() {
		Child x = Child.builder().field3(0.0).field1(5).item("").build().toBuilder().build();
	}
}
