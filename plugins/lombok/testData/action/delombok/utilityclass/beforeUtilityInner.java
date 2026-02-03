class UtilityInner {
	static class InnerInner {
		@lombok.experimental.UtilityClass
		class InnerInnerInner {
			int member;
		}
	}
	
	enum UtilityInsideEnum {
		FOO, BAR;
		
		@lombok.experimental.UtilityClass
		class InsideEnum {
			int member;
		}
	}
	
	interface UtilityInsideInterface {
		@lombok.experimental.UtilityClass
		class InsideInterface {
			int member;
		}
	}
}
