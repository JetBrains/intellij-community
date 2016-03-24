import java.util.List;
import java.util.Collection;
class BuilderSingularLists<T> {
	private List<T> children;
	private Collection<? extends Number> scarves;
	@SuppressWarnings("all")
	private List rawList;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularLists(final List<T> children, final Collection<? extends Number> scarves, final List rawList) {
		this.children = children;
		this.scarves = scarves;
		this.rawList = rawList;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularListsBuilder<T> {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<T> children;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<Number> scarves;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<java.lang.Object> rawList;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularListsBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> child(final T child) {
			if (this.children == null) this.children = new java.util.ArrayList<T>();
			this.children.add(child);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> children(final java.util.Collection<? extends T> children) {
			if (this.children == null) this.children = new java.util.ArrayList<T>();
			this.children.addAll(children);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> clearChildren() {
			if (this.children != null) this.children.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> scarf(final Number scarf) {
			if (this.scarves == null) this.scarves = new java.util.ArrayList<Number>();
			this.scarves.add(scarf);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> scarves(final java.util.Collection<? extends Number> scarves) {
			if (this.scarves == null) this.scarves = new java.util.ArrayList<Number>();
			this.scarves.addAll(scarves);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> clearScarves() {
			if (this.scarves != null) this.scarves.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> rawList(final java.lang.Object rawList) {
			if (this.rawList == null) this.rawList = new java.util.ArrayList<java.lang.Object>();
			this.rawList.add(rawList);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> rawList(final java.util.Collection<?> rawList) {
			if (this.rawList == null) this.rawList = new java.util.ArrayList<java.lang.Object>();
			this.rawList.addAll(rawList);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularListsBuilder<T> clearRawList() {
			if (this.rawList != null) this.rawList.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularLists<T> build() {
			java.util.List<T> children;
			switch (this.children == null ? 0 : this.children.size()) {
			case 0: 
				children = java.util.Collections.emptyList();
				break;
			case 1: 
				children = java.util.Collections.singletonList(this.children.get(0));
				break;
			default: 
				children = java.util.Collections.unmodifiableList(new java.util.ArrayList<T>(this.children));
			}
			java.util.Collection<Number> scarves;
			switch (this.scarves == null ? 0 : this.scarves.size()) {
			case 0: 
				scarves = java.util.Collections.emptyList();
				break;
			case 1: 
				scarves = java.util.Collections.singletonList(this.scarves.get(0));
				break;
			default: 
				scarves = java.util.Collections.unmodifiableList(new java.util.ArrayList<Number>(this.scarves));
			}
			java.util.List<java.lang.Object> rawList;
			switch (this.rawList == null ? 0 : this.rawList.size()) {
			case 0: 
				rawList = java.util.Collections.emptyList();
				break;
			case 1: 
				rawList = java.util.Collections.singletonList(this.rawList.get(0));
				break;
			default: 
				rawList = java.util.Collections.unmodifiableList(new java.util.ArrayList<java.lang.Object>(this.rawList));
			}
			return new BuilderSingularLists<T>(children, scarves, rawList);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSingularLists.BuilderSingularListsBuilder(children=" + this.children + ", scarves=" + this.scarves + ", rawList=" + this.rawList + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> BuilderSingularListsBuilder<T> builder() {
		return new BuilderSingularListsBuilder<T>();
	}
}
