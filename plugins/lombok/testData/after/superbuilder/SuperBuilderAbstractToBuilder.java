public class SuperBuilderAbstractToBuilder {
	public static class Parent {
		int parentField;
		@SuppressWarnings("all")
		public static abstract class ParentBuilder<C extends Parent, B extends ParentBuilder<C, B>> {
			@SuppressWarnings("all")
			private int parentField;
			@SuppressWarnings("all")
			protected B $fillValuesFrom(final C instance) {
				ParentBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
				return self();
			}
			@SuppressWarnings("all")
			private static void $fillValuesFromInstanceIntoBuilder(final Parent instance, final ParentBuilder<?, ?> b) {
				b.parentField(instance.parentField);
			}
			@SuppressWarnings("all")
			protected abstract B self();
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B parentField(final int parentField) {
				this.parentField = parentField;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderAbstractToBuilder.Parent.ParentBuilder(parentField=" + this.parentField + ")";
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
			this.parentField = b.parentField;
		}
		@SuppressWarnings("all")
		public static ParentBuilder<?, ?> builder() {
			return new ParentBuilderImpl();
		}
		@SuppressWarnings("all")
		public ParentBuilder<?, ?> toBuilder() {
			return new ParentBuilderImpl().$fillValuesFrom(this);
		}
	}
	public static abstract class Child extends Parent {
		double childField;
		@SuppressWarnings("all")
		public static abstract class ChildBuilder<C extends Child, B extends ChildBuilder<C, B>> extends Parent.ParentBuilder<C, B> {
			@SuppressWarnings("all")
			private double childField;
			@Override
			@SuppressWarnings("all")
			protected B $fillValuesFrom(final C instance) {
				super.$fillValuesFrom(instance);
				ChildBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
				return self();
			}
			@SuppressWarnings("all")
			private static void $fillValuesFromInstanceIntoBuilder(final Child instance, final ChildBuilder<?, ?> b) {
				b.childField(instance.childField);
			}
			@Override
			@SuppressWarnings("all")
			protected abstract B self();
			@Override
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B childField(final double childField) {
				this.childField = childField;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderAbstractToBuilder.Child.ChildBuilder(super=" + super.toString() + ", childField=" + this.childField + ")";
			}
		}
		@SuppressWarnings("all")
		protected Child(final ChildBuilder<?, ?> b) {
			super(b);
			this.childField = b.childField;
		}
	}
	public static class GrandChild extends Child {
		String grandChildField;
		@SuppressWarnings("all")
		public static abstract class GrandChildBuilder<C extends GrandChild, B extends GrandChildBuilder<C, B>> extends Child.ChildBuilder<C, B> {
			@SuppressWarnings("all")
			private String grandChildField;
			@Override
			@SuppressWarnings("all")
			protected B $fillValuesFrom(final C instance) {
				super.$fillValuesFrom(instance);
				GrandChildBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
				return self();
			}
			@SuppressWarnings("all")
			private static void $fillValuesFromInstanceIntoBuilder(final GrandChild instance, final GrandChildBuilder<?, ?> b) {
				b.grandChildField(instance.grandChildField);
			}
			@Override
			@SuppressWarnings("all")
			protected abstract B self();
			@Override
			@SuppressWarnings("all")
			public abstract C build();
			@SuppressWarnings("all")
			public B grandChildField(final String grandChildField) {
				this.grandChildField = grandChildField;
				return self();
			}
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "SuperBuilderAbstractToBuilder.GrandChild.GrandChildBuilder(super=" + super.toString() + ", grandChildField=" + this.grandChildField + ")";
			}
		}
		@SuppressWarnings("all")
		private static final class GrandChildBuilderImpl extends GrandChildBuilder<GrandChild, GrandChildBuilderImpl> {
			@SuppressWarnings("all")
			private GrandChildBuilderImpl() {
			}
			@Override
			@SuppressWarnings("all")
			protected GrandChildBuilderImpl self() {
				return this;
			}
			@Override
			@SuppressWarnings("all")
			public GrandChild build() {
				return new GrandChild(this);
			}
		}
		@SuppressWarnings("all")
		protected GrandChild(final GrandChildBuilder<?, ?> b) {
			super(b);
			this.grandChildField = b.grandChildField;
		}
		@SuppressWarnings("all")
		public static GrandChildBuilder<?, ?> builder() {
			return new GrandChildBuilderImpl();
		}
		@SuppressWarnings("all")
		public GrandChildBuilder<?, ?> toBuilder() {
			return new GrandChildBuilderImpl().$fillValuesFrom(this);
		}
	}
	public static void test() {
		GrandChild x = GrandChild.builder().grandChildField("").parentField(5).childField(2.5).build().toBuilder().build();
	}
}
