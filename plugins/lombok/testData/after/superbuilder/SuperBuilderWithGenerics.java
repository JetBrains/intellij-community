import java.util.List;

public class SuperBuilderWithGenerics {
	public static class Parent<A> {
		A field1;
		List<String> items;
		@SuppressWarnings("all")
		public static abstract class ParentBuilder<A, C extends Parent<A>, B extends ParentBuilder<A, C, B>> {
			@SuppressWarnings("all")
			private A field1;
			@SuppressWarnings("all")
			private java.util.ArrayList<String> items;
			@SuppressWarnings("all")
			protected abstract B self();
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B field1(final A field1) {
				this.field1 = field1;
				return self();
			}
			@SuppressWarnings("all")
			public B item(final String item) {
				if (this.items == null) this.items = new java.util.ArrayList<String>();
				this.items.add(item);
				return self();
			}
			@SuppressWarnings("all")
			public B items(final java.util.Collection<? extends String> items) {
				if (this.items == null) this.items = new java.util.ArrayList<String>();
				this.items.addAll(items);
				return self();
			}
			@SuppressWarnings("all")
			public B clearItems() {
				if (this.items != null) this.items.clear();
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithGenerics.Parent.ParentBuilder(field1=" + this.field1 + ", items=" + this.items + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class ParentBuilderImpl<A> extends ParentBuilder<A, Parent<A>, ParentBuilderImpl<A>> {
			@SuppressWarnings("all")
			private ParentBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected ParentBuilderImpl<A> self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public Parent<A> build() {
				return new Parent<A>(this);
			}
		}
		@SuppressWarnings("all")
		protected Parent(final ParentBuilder<A, ?, ?> b) {
			this.field1 = b.field1;
			java.util.List<String> items;
			switch (b.items == null ? 0 : b.items.size()) {
			case 0:
				items = java.util.Collections.emptyList();
				break;
			case 1:
				items = java.util.Collections.singletonList(b.items.get(0));
				break;
			default:
				items = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(b.items));
			}
			this.items = items;
		}
		@SuppressWarnings("all")
		public static <A> ParentBuilder<A, ?, ?> builder() {
			return new ParentBuilderImpl<A>();
		}
	}
	public static class Child<A> extends Parent<A> {
		double field3;
		@SuppressWarnings("all")
		public static abstract class ChildBuilder<A, C extends Child<A>, B extends ChildBuilder<A, C, B>> extends Parent.ParentBuilder<A, C, B> {
			@SuppressWarnings("all")
			private double field3;
			@Override
			@SuppressWarnings("all")
			protected abstract B self();
			@Override
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B field3(final double field3) {
				this.field3 = field3;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithGenerics.Child.ChildBuilder(super=" + super.toString() + ", field3=" + this.field3 + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class ChildBuilderImpl<A> extends ChildBuilder<A, Child<A>, ChildBuilderImpl<A>> {
			@SuppressWarnings("all")
			private ChildBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected ChildBuilderImpl<A> self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public Child<A> build() {
				return new Child<A>(this);
			}
		}
		@SuppressWarnings("all")
		protected Child(final ChildBuilder<A, ?, ?> b) {
			super(b);
			this.field3 = b.field3;
		}
		@SuppressWarnings("all")
		public static <A> ChildBuilder<A, ?, ?> builder() {
			return new ChildBuilderImpl<A>();
		}
	}
	public static void test() {
		Child<Integer> x = Child.<Integer>builder().field3(0.0).field1(5).item("").build();
	}
}
