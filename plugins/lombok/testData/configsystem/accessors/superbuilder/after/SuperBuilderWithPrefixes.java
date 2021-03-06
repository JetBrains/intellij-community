class SuperBuilderWithPrefixes {
	int mField;
	int xOtherField;
	java.util.List<String> mItems;

	public static abstract class SuperBuilderWithPrefixesBuilder<C extends SuperBuilderWithPrefixes, B extends SuperBuilderWithPrefixesBuilder<C, B>> {

		private int field;

		private int otherField;

		private java.util.ArrayList<String> items;

		protected abstract B self();

		public abstract C build();

		public B field(final int field) {
			this.field = field;
			return self();
		}

		public B otherField(final int otherField) {
			this.otherField = otherField;
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

//		@Override
		public String toString() {
			return "SuperBuilderWithPrefixes.SuperBuilderWithPrefixesBuilder(field=" + this.field + ", otherField=" + this.otherField + ", items=" + this.items + ")";
		}
	}

	private static final class SuperBuilderWithPrefixesBuilderImpl extends SuperBuilderWithPrefixesBuilder<SuperBuilderWithPrefixes, SuperBuilderWithPrefixesBuilderImpl> {

		private SuperBuilderWithPrefixesBuilderImpl() {
		}

//		@Override
		protected SuperBuilderWithPrefixesBuilderImpl self() {
			return this;
		}

//		@Override
		public SuperBuilderWithPrefixes build() {
			return new SuperBuilderWithPrefixes(this);
		}
	}

	protected SuperBuilderWithPrefixes(final SuperBuilderWithPrefixesBuilder<?, ?> b) {
		this.mField = b.field;
		this.xOtherField = b.otherField;
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
		this.mItems = items;
	}

	public static SuperBuilderWithPrefixesBuilder<?, ?> builder() {
		return new SuperBuilderWithPrefixesBuilderImpl();
	}
}
