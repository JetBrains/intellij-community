import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;
class BuilderSingularGuavaListsSets<T> {
	private ImmutableList<T> cards;
	private ImmutableCollection<? extends Number> frogs;
	@SuppressWarnings("all")
	private ImmutableSet rawSet;
	private ImmutableSortedSet<String> passes;
	private ImmutableTable<? extends Number, ? extends Number, String> users;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularGuavaListsSets(final ImmutableList<T> cards, final ImmutableCollection<? extends Number> frogs, final ImmutableSet rawSet, final ImmutableSortedSet<String> passes, final ImmutableTable<? extends Number, ? extends Number, String> users) {
		this.cards = cards;
		this.frogs = frogs;
		this.rawSet = rawSet;
		this.passes = passes;
		this.users = users;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularGuavaListsSetsBuilder<T> {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableList.Builder<T> cards;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableList.Builder<Number> frogs;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableSet.Builder<java.lang.Object> rawSet;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableSortedSet.Builder<String> passes;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableTable.Builder<Number, Number, String> users;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularGuavaListsSetsBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> card(final T card) {
			if (this.cards == null) this.cards = com.google.common.collect.ImmutableList.builder();
			this.cards.add(card);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> cards(final java.lang.Iterable<? extends T> cards) {
			if (this.cards == null) this.cards = com.google.common.collect.ImmutableList.builder();
			this.cards.addAll(cards);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> clearCards() {
			this.cards = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> frog(final Number frog) {
			if (this.frogs == null) this.frogs = com.google.common.collect.ImmutableList.builder();
			this.frogs.add(frog);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> frogs(final java.lang.Iterable<? extends Number> frogs) {
			if (this.frogs == null) this.frogs = com.google.common.collect.ImmutableList.builder();
			this.frogs.addAll(frogs);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> clearFrogs() {
			this.frogs = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> rawSet(final java.lang.Object rawSet) {
			if (this.rawSet == null) this.rawSet = com.google.common.collect.ImmutableSet.builder();
			this.rawSet.add(rawSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> rawSet(final java.lang.Iterable<?> rawSet) {
			if (this.rawSet == null) this.rawSet = com.google.common.collect.ImmutableSet.builder();
			this.rawSet.addAll(rawSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> clearRawSet() {
			this.rawSet = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> pass(final String pass) {
			if (this.passes == null) this.passes = com.google.common.collect.ImmutableSortedSet.naturalOrder();
			this.passes.add(pass);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> passes(final java.lang.Iterable<? extends String> passes) {
			if (this.passes == null) this.passes = com.google.common.collect.ImmutableSortedSet.naturalOrder();
			this.passes.addAll(passes);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> clearPasses() {
			this.passes = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> user(final Number rowKey, final Number columnKey, final String value) {
			if (this.users == null) this.users = com.google.common.collect.ImmutableTable.builder();
			this.users.put(rowKey, columnKey, value);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> users(final com.google.common.collect.Table<? extends Number, ? extends Number, ? extends String> users) {
			if (this.users == null) this.users = com.google.common.collect.ImmutableTable.builder();
			this.users.putAll(users);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> clearUsers() {
			this.users = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSets<T> build() {
			com.google.common.collect.ImmutableList<T> cards = this.cards == null ? com.google.common.collect.ImmutableList.<T>of() : this.cards.build();
			com.google.common.collect.ImmutableCollection<Number> frogs = this.frogs == null ? com.google.common.collect.ImmutableList.<Number>of() : this.frogs.build();
			com.google.common.collect.ImmutableSet<java.lang.Object> rawSet = this.rawSet == null ? com.google.common.collect.ImmutableSet.<java.lang.Object>of() : this.rawSet.build();
			com.google.common.collect.ImmutableSortedSet<String> passes = this.passes == null ? com.google.common.collect.ImmutableSortedSet.<String>of() : this.passes.build();
			com.google.common.collect.ImmutableTable<Number, Number, String> users = this.users == null ? com.google.common.collect.ImmutableTable.<Number, Number, String>of() : this.users.build();
			return new BuilderSingularGuavaListsSets<T>(cards, frogs, rawSet, passes, users);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSingularGuavaListsSets.BuilderSingularGuavaListsSetsBuilder(cards=" + this.cards + ", frogs=" + this.frogs + ", rawSet=" + this.rawSet + ", passes=" + this.passes + ", users=" + this.users + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> BuilderSingularGuavaListsSetsBuilder<T> builder() {
		return new BuilderSingularGuavaListsSetsBuilder<T>();
	}
}