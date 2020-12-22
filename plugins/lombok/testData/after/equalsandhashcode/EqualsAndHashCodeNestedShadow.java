interface EqualsAndHashCodeNestedShadow {
	interface Foo {
	}
	class Bar {
		public static class Foo extends Bar implements EqualsAndHashCodeNestedShadow.Foo {
			@Override
			@SuppressWarnings("all")
			public boolean equals(final Object o) {
				if (o == this) return true;
				if (!(o instanceof EqualsAndHashCodeNestedShadow.Bar.Foo)) return false;
				final EqualsAndHashCodeNestedShadow.Bar.Foo other = (EqualsAndHashCodeNestedShadow.Bar.Foo) o;
				if (!other.canEqual((Object) this)) return false;
				return true;
			}
			@SuppressWarnings("all")
			protected boolean canEqual(final Object other) {
				return other instanceof EqualsAndHashCodeNestedShadow.Bar.Foo;
			}
			@Override
			@SuppressWarnings("all")
			public int hashCode() {
				int result = 1;
				return result;
			}
		}
	}
	class Baz {
		public static class Foo<T> extends Bar implements EqualsAndHashCodeNestedShadow.Foo {
			@Override
			@SuppressWarnings("all")
			public boolean equals(final Object o) {
				if (o == this) return true;
				if (!(o instanceof EqualsAndHashCodeNestedShadow.Baz.Foo)) return false;
				final EqualsAndHashCodeNestedShadow.Baz.Foo<?> other = (EqualsAndHashCodeNestedShadow.Baz.Foo<?>) o;
				if (!other.canEqual((Object) this)) return false;
				return true;
			}
			@SuppressWarnings("all")
			protected boolean canEqual(final Object other) {
				return other instanceof EqualsAndHashCodeNestedShadow.Baz.Foo;
			}
			@Override
			@SuppressWarnings("all")
			public int hashCode() {
				int result = 1;
				return result;
			}
		}
	}
}
