import java.util.Collection;
import java.util.NavigableMap;
import java.util.Set;
class BuilderSingularRedirectToGuava {
	private Set<String> dangerMice;
	private NavigableMap<Integer, Number> things;
	private Collection<Class<?>> doohickeys;
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularRedirectToGuava(final Set<String> dangerMice, final NavigableMap<Integer, Number> things, final Collection<Class<?>> doohickeys) {
		this.dangerMice = dangerMice;
		this.things = things;
		this.doohickeys = doohickeys;
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularRedirectToGuavaBuilder {
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableSet.Builder<String> dangerMice;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableSortedMap.Builder<Integer, Number> things;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableList.Builder<Class<?>> doohickeys;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularRedirectToGuavaBuilder() {
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder dangerMouse(final String dangerMouse) {
			if (this.dangerMice == null) this.dangerMice = com.google.common.collect.ImmutableSet.builder();
			this.dangerMice.add(dangerMouse);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder dangerMice(final Iterable<? extends String> dangerMice) {
			if (this.dangerMice == null) this.dangerMice = com.google.common.collect.ImmutableSet.builder();
			this.dangerMice.addAll(dangerMice);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder thing(final Integer thing$key, final Number thing$value) {
			if (this.things == null) this.things = com.google.common.collect.ImmutableSortedMap.naturalOrder();
			this.things.put(thing$key, thing$value);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder things(final java.util.Map<? extends Integer, ? extends Number> things) {
			if (this.things == null) this.things = com.google.common.collect.ImmutableSortedMap.naturalOrder();
			this.things.putAll(things);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder doohickey(final Class<?> doohickey) {
			if (this.doohickeys == null) this.doohickeys = com.google.common.collect.ImmutableList.builder();
			this.doohickeys.add(doohickey);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuavaBuilder doohickeys(final Iterable<? extends Class<?>> doohickeys) {
			if (this.doohickeys == null) this.doohickeys = com.google.common.collect.ImmutableList.builder();
			this.doohickeys.addAll(doohickeys);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularRedirectToGuava build() {
			Set<String> dangerMice = this.dangerMice == null ? com.google.common.collect.ImmutableSet.<String>of() : this.dangerMice.build();
			NavigableMap<Integer, Number> things = this.things == null ? com.google.common.collect.ImmutableSortedMap.<Integer, Number>of() : this.things.build();
			Collection<Class<?>> doohickeys = this.doohickeys == null ? com.google.common.collect.ImmutableList.<Class<?>>of() : this.doohickeys.build();
			return new BuilderSingularRedirectToGuava(dangerMice, things, doohickeys);
		}
		@Override
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public String toString() {
			return "BuilderSingularRedirectToGuava.BuilderSingularRedirectToGuavaBuilder(dangerMice=" + this.dangerMice + ", things=" + this.things + ", doohickeys=" + this.doohickeys + ")";
		}
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderSingularRedirectToGuavaBuilder builder() {
		return new BuilderSingularRedirectToGuavaBuilder();
	}
}