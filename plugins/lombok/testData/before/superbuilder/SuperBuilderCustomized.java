public class SuperBuilderCustomized {
	@lombok.experimental.SuperBuilder
	public static class Parent {
		public static abstract class ParentBuilder<C extends Parent, B extends ParentBuilder<C, B>> {
			public B resetToDefault() {
				field1 = 0;
				return self();
			}
		}
		int field1;
	}
	
	@lombok.experimental.SuperBuilder
	public static class Child extends Parent {
		private static final class ChildBuilderImpl extends ChildBuilder<Child, ChildBuilderImpl> {
			@Override
			public Child build() {
				this.resetToDefault();
				return new Child(this);
			}
		}
		double field2;
		public static ChildBuilder<?, ?> builder() {
			return new ChildBuilderImpl().field2(10.0);
		}
	}
	
	public static void test() {
		Child x = Child.builder().field2(1.0).field1(5).resetToDefault().build();
	}
}
