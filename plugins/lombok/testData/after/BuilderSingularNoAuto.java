import java.util.List;
class BuilderSingularNoAuto {
	private List<String> things;
	private List<String> widgets;
	private List<String> items;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularNoAuto(final List<String> things, final List<String> widgets, final List<String> items) {
		this.things = things;
		this.widgets = widgets;
		this.items = items;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularNoAutoBuilder {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<String> things;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<String> widgets;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<String> items;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularNoAutoBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder things(final String things) {
			if (this.things == null) this.things = new java.util.ArrayList<String>();
			this.things.add(things);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder things(final java.util.Collection<? extends String> things) {
			if (this.things == null) this.things = new java.util.ArrayList<String>();
			this.things.addAll(things);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder clearThings() {
			if (this.things != null) this.things.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder widget(final String widget) {
			if (this.widgets == null) this.widgets = new java.util.ArrayList<String>();
			this.widgets.add(widget);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder widgets(final java.util.Collection<? extends String> widgets) {
			if (this.widgets == null) this.widgets = new java.util.ArrayList<String>();
			this.widgets.addAll(widgets);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder clearWidgets() {
			if (this.widgets != null) this.widgets.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder items(final String items) {
			if (this.items == null) this.items = new java.util.ArrayList<String>();
			this.items.add(items);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder items(final java.util.Collection<? extends String> items) {
			if (this.items == null) this.items = new java.util.ArrayList<String>();
			this.items.addAll(items);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAutoBuilder clearItems() {
			if (this.items != null) this.items.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularNoAuto build() {
			java.util.List<String> things;
			switch (this.things == null ? 0 : this.things.size()) {
			case 0: 
				things = java.util.Collections.emptyList();
				break;
			case 1: 
				things = java.util.Collections.singletonList(this.things.get(0));
				break;
			default: 
				things = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.things));
			}
			java.util.List<String> widgets;
			switch (this.widgets == null ? 0 : this.widgets.size()) {
			case 0: 
				widgets = java.util.Collections.emptyList();
				break;
			case 1: 
				widgets = java.util.Collections.singletonList(this.widgets.get(0));
				break;
			default: 
				widgets = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.widgets));
			}
			java.util.List<String> items;
			switch (this.items == null ? 0 : this.items.size()) {
			case 0: 
				items = java.util.Collections.emptyList();
				break;
			case 1: 
				items = java.util.Collections.singletonList(this.items.get(0));
				break;
			default: 
				items = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.items));
			}
			return new BuilderSingularNoAuto(things, widgets, items);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSingularNoAuto.BuilderSingularNoAutoBuilder(things=" + this.things + ", widgets=" + this.widgets + ", items=" + this.items + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderSingularNoAutoBuilder builder() {
		return new BuilderSingularNoAutoBuilder();
	}
}