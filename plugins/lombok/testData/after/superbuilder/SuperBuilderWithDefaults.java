public class SuperBuilderWithDefaults {
	public static class Parent<N extends Number> {
		private long millis;
		private N numberField;
		@SuppressWarnings("all")
		private static <N extends Number> long $default$millis() {
			return System.currentTimeMillis();
		}
		@SuppressWarnings("all")
		private static <N extends Number> N $default$numberField() {
			return null;
		}
		@SuppressWarnings("all")
		public static abstract class ParentBuilder<N extends Number, C extends Parent<N>, B extends ParentBuilder<N, C, B>> {
			@SuppressWarnings("all")
			private boolean millis$set;
			@SuppressWarnings("all")
			private long millis;
			@SuppressWarnings("all")
			private boolean numberField$set;
			@SuppressWarnings("all")
			private N numberField;
			@SuppressWarnings("all")
			protected abstract B self();
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B millis(final long millis) {
				this.millis = millis;
				millis$set = true;
				return self();
			}
			@SuppressWarnings("all")
			public B numberField(final N numberField) {
				this.numberField = numberField;
				numberField$set = true;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithDefaults.Parent.ParentBuilder(millis=" + this.millis + ", numberField=" + this.numberField + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class ParentBuilderImpl<N extends Number> extends ParentBuilder<N, Parent<N>, ParentBuilderImpl<N>> {
			@SuppressWarnings("all")
			private ParentBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected ParentBuilderImpl<N> self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public Parent<N> build() {
				return new Parent<N>(this);
			}
		}
		@SuppressWarnings("all")
		protected Parent(final ParentBuilder<N, ?, ?> b) {
			if (b.millis$set) this.millis = b.millis;
			 else this.millis = Parent.<N>$default$millis();
			if (b.numberField$set) this.numberField = b.numberField; 
			 else this.numberField = Parent.<N>$default$numberField();
		}
		@SuppressWarnings("all")
		public static <N extends Number> ParentBuilder<N, ?, ?> builder() {
			return new ParentBuilderImpl<N>();
		}
	}
	public static class Child extends Parent<Integer> {
		private double doubleField;
		@SuppressWarnings("all")
		private static double $default$doubleField() {
			return Math.PI;
		}
		@SuppressWarnings("all")
		public static abstract class ChildBuilder<C extends Child, B extends ChildBuilder<C, B>> extends Parent.ParentBuilder<Integer, C, B> {
			@SuppressWarnings("all")
			private boolean doubleField$set;
			@SuppressWarnings("all")
			private double doubleField;
			@Override
			@SuppressWarnings("all")
			protected abstract B self();
			@Override
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B doubleField(final double doubleField) {
				this.doubleField = doubleField;
				doubleField$set = true;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithDefaults.Child.ChildBuilder(super=" + super.toString() + ", doubleField=" + this.doubleField + ")";
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
			if (b.doubleField$set) this.doubleField = b.doubleField;
			 else this.doubleField = Child.$default$doubleField();
		}
		@SuppressWarnings("all")
		public static ChildBuilder<?, ?> builder() {
			return new ChildBuilderImpl();
		}
	}
	public static void test() {
		Child x = Child.builder().doubleField(0.1).numberField(5).millis(1234567890L).build();
	}
}
