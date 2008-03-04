public class RenameCollisions {
	public static final int <caret>STATIC_FIELD = 5;
	private int myField = 7;

	public static class StaticInnerClass {
		public static final int SI_STATIC_FIELD = 9;
		private int mySiField = 11;

		public void instanceContext(int param) {
			int localVar = 0;
			int var1 = localVar + param + SI_STATIC_FIELD + mySiField + STATIC_FIELD;
		}
		public static void staticContext(int param) {
			int localVar = 0;
			int var1 = localVar + param + SI_STATIC_FIELD + STATIC_FIELD;
		}
	}

	private class InnerClass {
		public static final int INNER_STATIC_FIELD = 13;
		private int myInnerField = 15;

		public void instanceContext(int param) {
			int localVar = 0;
			int var1 = localVar + param + INNER_STATIC_FIELD + myInnerField + STATIC_FIELD + myField;
		}
	}
}
