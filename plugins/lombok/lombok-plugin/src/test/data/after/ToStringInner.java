class ToStringOuter {
	int x;
	String name;
	class ToStringInner {
		int y;
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "ToStringOuter.ToStringInner(y=" + this.y + ")";
		}
	}
	static class ToStringStaticInner {
		int y;
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "ToStringOuter.ToStringStaticInner(y=" + this.y + ")";
		}
	}
	class ToStringMiddle {
		class ToStringMoreInner {
			String name;
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public java.lang.String toString() {
				return "ToStringOuter.ToStringMiddle.ToStringMoreInner(name=" + this.name + ")";
			}
		}
	}
	
	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "ToStringOuter(x=" + this.x + ", name=" + this.name + ")";
	}
}