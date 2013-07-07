import lombok.ToString;
@ToString class ToStringOuter {
	int x;
	String name;
	@ToString class ToStringInner {
		 int y;
	}
	@ToString static class ToStringStaticInner {
		int y;
	}
	class ToStringMiddle {
		@ToString class ToStringMoreInner {
			String name;
		}
	}
}