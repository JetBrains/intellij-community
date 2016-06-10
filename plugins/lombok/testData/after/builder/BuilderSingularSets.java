import java.util.Set;
import java.util.SortedSet;
class BuilderSingularSets<T> {
	private Set<T> dangerMice;
	private SortedSet<? extends Number> octopodes;
	@SuppressWarnings("all")
	private Set rawSet;
	private Set<String> stringSet;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularSets(final Set<T> dangerMice, final SortedSet<? extends Number> octopodes, final Set rawSet, final Set<String> stringSet) {
		this.dangerMice = dangerMice;
		this.octopodes = octopodes;
		this.rawSet = rawSet;
		this.stringSet = stringSet;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularSetsBuilder<T> {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<T> dangerMice;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<Number> octopodes;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<java.lang.Object> rawSet;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<String> stringSet;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularSetsBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> dangerMouse(final T dangerMouse) {
			if (this.dangerMice == null) this.dangerMice = new java.util.ArrayList<T>();
			this.dangerMice.add(dangerMouse);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> dangerMice(final java.util.Collection<? extends T> dangerMice) {
			if (this.dangerMice == null) this.dangerMice = new java.util.ArrayList<T>();
			this.dangerMice.addAll(dangerMice);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> clearDangerMice() {
			if (this.dangerMice != null) this.dangerMice.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> octopus(final Number octopus) {
			if (this.octopodes == null) this.octopodes = new java.util.ArrayList<Number>();
			this.octopodes.add(octopus);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> octopodes(final java.util.Collection<? extends Number> octopodes) {
			if (this.octopodes == null) this.octopodes = new java.util.ArrayList<Number>();
			this.octopodes.addAll(octopodes);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> clearOctopodes() {
			if (this.octopodes != null) this.octopodes.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> rawSet(final java.lang.Object rawSet) {
			if (this.rawSet == null) this.rawSet = new java.util.ArrayList<java.lang.Object>();
			this.rawSet.add(rawSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> rawSet(final java.util.Collection<?> rawSet) {
			if (this.rawSet == null) this.rawSet = new java.util.ArrayList<java.lang.Object>();
			this.rawSet.addAll(rawSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> clearRawSet() {
			if (this.rawSet != null) this.rawSet.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> stringSet(final String stringSet) {
			if (this.stringSet == null) this.stringSet = new java.util.ArrayList<String>();
			this.stringSet.add(stringSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> stringSet(final java.util.Collection<? extends String> stringSet) {
			if (this.stringSet == null) this.stringSet = new java.util.ArrayList<String>();
			this.stringSet.addAll(stringSet);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSetsBuilder<T> clearStringSet() {
			if (this.stringSet != null) this.stringSet.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularSets<T> build() {
			java.util.Set<T> dangerMice;
			switch (this.dangerMice == null ? 0 : this.dangerMice.size()) {
			case 0: 
				dangerMice = java.util.Collections.emptySet();
				break;
			case 1: 
				dangerMice = java.util.Collections.singleton(this.dangerMice.get(0));
				break;
			default: 
				dangerMice = new java.util.LinkedHashSet<T>(this.dangerMice.size() < 1073741824 ? 1 + this.dangerMice.size() + (this.dangerMice.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
				dangerMice.addAll(this.dangerMice);
				dangerMice = java.util.Collections.unmodifiableSet(dangerMice);
			}
			java.util.SortedSet<Number> octopodes = new java.util.TreeSet<Number>();
			if (this.octopodes != null) octopodes.addAll(this.octopodes);
			octopodes = java.util.Collections.unmodifiableSortedSet(octopodes);
			java.util.Set<java.lang.Object> rawSet;
			switch (this.rawSet == null ? 0 : this.rawSet.size()) {
			case 0: 
				rawSet = java.util.Collections.emptySet();
				break;
			case 1: 
				rawSet = java.util.Collections.singleton(this.rawSet.get(0));
				break;
			default: 
				rawSet = new java.util.LinkedHashSet<java.lang.Object>(this.rawSet.size() < 1073741824 ? 1 + this.rawSet.size() + (this.rawSet.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
				rawSet.addAll(this.rawSet);
				rawSet = java.util.Collections.unmodifiableSet(rawSet);
			}
			java.util.Set<String> stringSet;
			switch (this.stringSet == null ? 0 : this.stringSet.size()) {
			case 0: 
				stringSet = java.util.Collections.emptySet();
				break;
			case 1: 
				stringSet = java.util.Collections.singleton(this.stringSet.get(0));
				break;
			default: 
				stringSet = new java.util.LinkedHashSet<String>(this.stringSet.size() < 1073741824 ? 1 + this.stringSet.size() + (this.stringSet.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
				stringSet.addAll(this.stringSet);
				stringSet = java.util.Collections.unmodifiableSet(stringSet);
			}
			return new BuilderSingularSets<T>(dangerMice, octopodes, rawSet, stringSet);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSingularSets.BuilderSingularSetsBuilder(dangerMice=" + this.dangerMice + ", octopodes=" + this.octopodes + ", rawSet=" + this.rawSet + ", stringSet=" + this.stringSet + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> BuilderSingularSetsBuilder<T> builder() {
		return new BuilderSingularSetsBuilder<T>();
	}
}