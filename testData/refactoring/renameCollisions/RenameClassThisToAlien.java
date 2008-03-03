package renameCollisions;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.valueOf;

public class RenameCollisions<caret> {
	public static final int STATIC_FIELD = 5;
	public static final int SN_STATIC_FIELD = 6;
	public static void staticMethod() {
	}
	public static void snStaticMethod() {
	}
	public void method() {
	}
	public void snMethod() {
	}

	public void instanceContext() {
		CASE_INSENSITIVE_ORDER.getClass();
		staticMethod();
		valueOf(20);
		method();

		int var6 = RenameCollisions.SN_STATIC_FIELD;
		String.CASE_INSENSITIVE_ORDER.getClass();
		RenameCollisions.snStaticMethod();
		String.valueOf(20);
	}

	public static void staticContext() {
		CASE_INSENSITIVE_ORDER.getClass();
		staticMethod();
		valueOf(20);

		int var6 = RenameCollisions.SN_STATIC_FIELD;
		String.CASE_INSENSITIVE_ORDER.getClass();
		RenameCollisions.snStaticMethod();
		String.valueOf(20);
	}
}
