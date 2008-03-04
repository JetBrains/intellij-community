package renameCollisions;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.valueOf;

public class RenameCollisions<caret> {
	public static final int STATIC_FIELD = 5;
	public static void staticMethod() {
	}
	private int myField;
	public void method() {
	}

	public void instanceContext() {
		CASE_INSENSITIVE_ORDER.getClass();
		valueOf(0);

		String.CASE_INSENSITIVE_ORDER.getClass();
		String.valueOf(0);

		int var6 = STATIC_FIELD;
		staticMethod();

		myField++;
		method();
	}

	public static void staticContext() {
		CASE_INSENSITIVE_ORDER.getClass();
		valueOf(0);

		String.CASE_INSENSITIVE_ORDER.getClass();
		String.valueOf(0);

		int var6 = STATIC_FIELD;
		staticMethod();
	}
}
