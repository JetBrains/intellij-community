import static javax.swing.SwingConstants.BOTTOM;

public class RenameCollisions {
	public static class StaticInnerClass {
		public static void staticContext(int param<caret>) {
			int var1 = BOTTOM;
		}
	}
}
