package before.superbuilder;

import java.util.List;

public class SuperBuilderSimple {
	public static class Parent {
		int field1;
		List<String> items;

		public static abstract class ParentBuilder<C extends Parent, B extends ParentBuilder<C, B>> {

			private int field1;
			private java.util.ArrayList<String> items;

			protected abstract B self();
			public abstract C build();

			public B field1(final int field1) {
				this.field1 = field1;
				return self();
			}

			public B item(final String item) {
				if (this.items == null) this.items = new java.util.ArrayList<String>();
				this.items.add(item);
				return self();
			}

			public B items(final java.util.Collection<? extends String> items) {
				if (this.items == null) this.items = new java.util.ArrayList<String>();
				this.items.addAll(items);
				return self();
			}

			public B clearItems() {
				if (this.items != null) this.items.clear();
				return self();
			}

			@Override
			public String toString() {
				return "SuperBuilderBasic.Parent.ParentBuilder(field1=" + this.field1 + ", items=" + this.items + ")";
			}
		}

		private static final class ParentBuilderImpl extends ParentBuilder<Parent, ParentBuilderImpl> {

			private ParentBuilderImpl() {
			}

			@Override
			protected ParentBuilderImpl self() {
				return this;
			}

			@Override
			public Parent build() {
				return new Parent(this);
			}
		}

		protected Parent(final ParentBuilder<?, ?> b) {
			this.field1 = b.field1;
			List<String> items;
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

		public static ParentBuilder<?, ?> builder() {
			return new ParentBuilderImpl();
		}
	}

	public static class Child extends Parent {
		double field3;

		public static abstract class ChildBuilder<C extends Child, B extends ChildBuilder<C, B>> extends ParentBuilder<C, B> {

			private double field3;

			@Override
			protected abstract B self();

			@Override
			public abstract C build();

			public B field3(final double field3) {
				this.field3 = field3;
				return self();
			}

			@Override
			public String toString() {
				return "SuperBuilderBasic.Child.ChildBuilder(super=" + super.toString() + ", field3=" + this.field3 + ")";
			}
		}

		private static final class ChildBuilderImpl extends ChildBuilder<Child, ChildBuilderImpl> {

			private ChildBuilderImpl() {
			}

			@Override
			protected ChildBuilderImpl self() {
				return this;
			}

			@Override
			public Child build() {
				return new Child(this);
			}
		}

		protected Child(final ChildBuilder<?, ?> b) {
			super(b);
			this.field3 = b.field3;
		}

		public static ChildBuilder<?, ?> builder() {
			return new ChildBuilderImpl();
		}
	}

	public static void test() {
		Child x = Child.builder().field3(0.0).field1(5).item("").build();
	}
}
