import java.util.List;

public class SuperBuilderBasic {
	public static class Parent {
		int field1;
		List<String> items;
		@SuppressWarnings("all")
		public static abstract class ParentBuilder<C extends Parent, B extends ParentBuilder<C, B>> {
			@SuppressWarnings("all")
			private int field1;
			@SuppressWarnings("all")
			private java.util.ArrayList<String> items;
			@SuppressWarnings("all")
			protected abstract B self();
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B field1(final int field1) {
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
				return "SuperBuilderBasic.Parent.ParentBuilder(field1=" + this.field1 + ", items=" + this.items + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class ParentBuilderImpl extends ParentBuilder<Parent, ParentBuilderImpl> {
			@SuppressWarnings("all")
			private ParentBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected ParentBuilderImpl self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public Parent build() {
				return new Parent(this);
			}
		}
		@SuppressWarnings("all")
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
		@SuppressWarnings("all")
		public static ParentBuilder<?, ?> builder() {
			return new ParentBuilderImpl();
		}
	}
	public static class Child extends Parent {
		double field3;
		@SuppressWarnings("all")
		public static abstract class ChildBuilder<C extends Child, B extends ChildBuilder<C, B>> extends Parent.ParentBuilder<C, B> {
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
				return "SuperBuilderBasic.Child.ChildBuilder(super=" + super.toString() + ", field3=" + this.field3 + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class ChildBuilderImpl extends ChildBuilder<Child, ChildBuilderImpl> {
			@SuppressWarnings("all")
			private ChildBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected ChildBuilderImpl self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public Child build() {
				return new Child(this);
			}
		}
		@SuppressWarnings("all")
		protected Child(final ChildBuilder<?, ?> b) {
			super(b);
			this.field3 = b.field3;
		}
		@SuppressWarnings("all")
		public static ChildBuilder<?, ?> builder() {
			return new ChildBuilderImpl();
		}
	}
	public static void test() {
		Child x = Child.builder().field3(0.0).field1(5).item("").build();
	}
}
