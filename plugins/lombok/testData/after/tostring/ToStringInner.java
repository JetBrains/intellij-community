class ToStringOuter {
	int x;
	String name;
	class ToStringInner {
		int y;
		@Override
		@SuppressWarnings("all")
		public String toString() {
			return "ToStringOuter.ToStringInner(y=" + this.y + ")";
		}
	}
	static class ToStringStaticInner {
		int y;
		@Override
		@SuppressWarnings("all")
		public String toString() {
			return "ToStringOuter.ToStringStaticInner(y=" + this.y + ")";
		}
	}
	class ToStringMiddle {
		class ToStringMoreInner {
			String name;
			@Override
			@SuppressWarnings("all")
			public String toString() {
				return "ToStringOuter.ToStringMiddle.ToStringMoreInner(name=" + this.name + ")";
			}
		}
	}
	
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToStringOuter(x=" + this.x + ", name=" + this.name + ")";
	}
}
