@lombok.experimental.UtilityClass
class UtilityClass {
	private long someField = System.currentTimeMillis();
	
	void someMethod() {
		System.out.println();
	}
	
	protected class InnerClass {
		private String innerInnerMember;
	}
}
