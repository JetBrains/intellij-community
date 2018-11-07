class SuperBuilderWithPrefixes {
	int mField;
	int xOtherField;
	java.util.List<String> mItems;
	@SuppressWarnings("all")
	public static abstract class SuperBuilderWithPrefixesBuilder<C extends SuperBuilderWithPrefixes, B extends SuperBuilderWithPrefixesBuilder<C, B>> {
		@SuppressWarnings("all")
		private int field;
		@SuppressWarnings("all")
		private int otherField;
		@SuppressWarnings("all")
		private java.util.ArrayList<String> items;
		@SuppressWarnings("all")
		protected abstract B self();
		@SuppressWarnings("all")
		public abstract C build();
		@SuppressWarnings("all")
		public B field(final int field) {
			this.field = field;
			return self();
		}
		@SuppressWarnings("all")
		public B otherField(final int otherField) {
			this.otherField = otherField;
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
			return "SuperBuilderWithPrefixes.SuperBuilderWithPrefixesBuilder(field=" + this.field + ", otherField=" + this.otherField + ", items=" + this.items + ")";
		}
	}
	@SuppressWarnings("all")
	private static final class SuperBuilderWithPrefixesBuilderImpl extends SuperBuilderWithPrefixesBuilder<SuperBuilderWithPrefixes, SuperBuilderWithPrefixesBuilderImpl> {
		@SuppressWarnings("all")
		private SuperBuilderWithPrefixesBuilderImpl() {
		}
		@Override
		@SuppressWarnings("all")
		protected SuperBuilderWithPrefixesBuilderImpl self() {
			return this;
		}
		@Override
		@SuppressWarnings("all")
		public SuperBuilderWithPrefixes build() {
			return new SuperBuilderWithPrefixes(this);
		}
	}
	@SuppressWarnings("all")
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
	@SuppressWarnings("all")
	public static SuperBuilderWithPrefixesBuilder<?, ?> builder() {
		return new SuperBuilderWithPrefixesBuilderImpl();
	}
}
