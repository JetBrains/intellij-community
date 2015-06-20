import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
class BuilderSingularGuavaListsSets<T> {
	private ImmutableList<T> cards;
	private ImmutableCollection<? extends Number> frogs;
	@SuppressWarnings("all")
	private ImmutableSet rawSet;
	private ImmutableSortedSet<String> passes;
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularGuavaListsSets(final ImmutableList<T> cards, final ImmutableCollection<? extends Number> frogs, final ImmutableSet rawSet, final ImmutableSortedSet<String> passes) {
		this.cards = cards;
		this.frogs = frogs;
		this.rawSet = rawSet;
		this.passes = passes;
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularGuavaListsSetsBuilder<T> {
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableList.Builder<T> cards;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableList.Builder<Number> frogs;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableSet.Builder<Object> rawSet;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableSortedSet.Builder<String> passes;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularGuavaListsSetsBuilder() {
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> card(final T card) {
			if (this.cards == null) this.cards = ImmutableList.builder();
			this.cards.add(card);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> cards(final Iterable<? extends T> cards) {
			if (this.cards == null) this.cards = ImmutableList.builder();
			this.cards.addAll(cards);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> frog(final Number frog) {
			if (this.frogs == null) this.frogs = ImmutableList.builder();
			this.frogs.add(frog);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> frogs(final Iterable<? extends Number> frogs) {
			if (this.frogs == null) this.frogs = ImmutableList.builder();
			this.frogs.addAll(frogs);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> rawSet(final Object rawSet) {
			if (this.rawSet == null) this.rawSet = ImmutableSet.builder();
			this.rawSet.add(rawSet);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> rawSet(final Iterable<?> rawSet) {
			if (this.rawSet == null) this.rawSet = ImmutableSet.builder();
			this.rawSet.addAll(rawSet);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> pass(final String pass) {
			if (this.passes == null) this.passes = ImmutableSortedSet.naturalOrder();
			this.passes.add(pass);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSetsBuilder<T> passes(final Iterable<? extends String> passes) {
			if (this.passes == null) this.passes = ImmutableSortedSet.naturalOrder();
			this.passes.addAll(passes);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaListsSets<T> build() {
			ImmutableList<T> cards = this.cards == null ? ImmutableList.<T>of() : this.cards.build();
			ImmutableCollection<Number> frogs = this.frogs == null ? ImmutableList.<Number>of() : this.frogs.build();
			ImmutableSet<Object> rawSet = this.rawSet == null ? ImmutableSet.<Object>of() : this.rawSet.build();
			ImmutableSortedSet<String> passes = this.passes == null ? ImmutableSortedSet.<String>of() : this.passes.build();
			return new BuilderSingularGuavaListsSets<T>(cards, frogs, rawSet, passes);
		}
		@Override
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public String toString() {
			return "BuilderSingularGuavaListsSets.BuilderSingularGuavaListsSetsBuilder(cards=" + this.cards + ", frogs=" + this.frogs + ", rawSet=" + this.rawSet + ", passes=" + this.passes + ")";
		}
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> BuilderSingularGuavaListsSetsBuilder<T> builder() {
		return new BuilderSingularGuavaListsSetsBuilder<T>();
	}
}