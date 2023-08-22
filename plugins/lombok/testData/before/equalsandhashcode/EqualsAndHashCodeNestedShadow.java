interface EqualsAndHashCodeNestedShadow {
	interface Foo {
	}
	class Bar {
		@lombok.EqualsAndHashCode(callSuper=false)
		public static class Foo extends Bar implements EqualsAndHashCodeNestedShadow.Foo {
		}
	}
	
	class Baz {
		@lombok.EqualsAndHashCode(callSuper=false)
		public static class Foo<T> extends Bar implements EqualsAndHashCodeNestedShadow.Foo {
		}
	}
}