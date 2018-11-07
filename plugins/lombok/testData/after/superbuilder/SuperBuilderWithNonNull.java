public class SuperBuilderWithNonNull {
	public static class Parent {
		@lombok.NonNull
		final String nonNullParentField;
		@SuppressWarnings("all")
		private static String $default$nonNullParentField() {
			return "default";
		}
		@SuppressWarnings("all")
		public static abstract class ParentBuilder<C extends Parent, B extends ParentBuilder<C, B>> {
			@SuppressWarnings("all")
			private boolean nonNullParentField$set;
			@SuppressWarnings("all")
			private String nonNullParentField;
			@SuppressWarnings("all")
			protected abstract B self();
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B nonNullParentField(@lombok.NonNull final String nonNullParentField) {
				if (nonNullParentField == null) {
					throw new NullPointerException("nonNullParentField is marked @NonNull but is null");
				}
				this.nonNullParentField = nonNullParentField;
				nonNullParentField$set = true;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithNonNull.Parent.ParentBuilder(nonNullParentField=" + this.nonNullParentField + ")";
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
			if (b.nonNullParentField$set) this.nonNullParentField = b.nonNullParentField;
			 else this.nonNullParentField = Parent.$default$nonNullParentField();
			if (nonNullParentField == null) {
				throw new NullPointerException("nonNullParentField is marked @NonNull but is null");
			}
		}
		@SuppressWarnings("all")
		public static ParentBuilder<?, ?> builder() {
			return new ParentBuilderImpl();
		}
	}
	public static class Child extends Parent {
		@lombok.NonNull
		String nonNullChildField;
		@SuppressWarnings("all")
		public static abstract class ChildBuilder<C extends Child, B extends ChildBuilder<C, B>> extends Parent.ParentBuilder<C, B> {
			@SuppressWarnings("all")
			private String nonNullChildField;
			@Override
			@SuppressWarnings("all")
			protected abstract B self();
			@Override
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B nonNullChildField(@lombok.NonNull final String nonNullChildField) {
				if (nonNullChildField == null) {
					throw new NullPointerException("nonNullChildField is marked @NonNull but is null");
				}
				this.nonNullChildField = nonNullChildField;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderWithNonNull.Child.ChildBuilder(super=" + super.toString() + ", nonNullChildField=" + this.nonNullChildField + ")";
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
			this.nonNullChildField = b.nonNullChildField;
			if (nonNullChildField == null) {
				throw new NullPointerException("nonNullChildField is marked @NonNull but is null");
			}
		}
		@SuppressWarnings("all")
		public static ChildBuilder<?, ?> builder() {
			return new ChildBuilderImpl();
		}
	}
	public static void test() {
		Child x = Child.builder().nonNullChildField("child").nonNullParentField("parent").build();
	}
}
