package renameCollisions;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.valueOf;

class <caret>LocalClass {
	public static final int STATIC_FIELD = 5;
	public static void staticMethod() {
	}
	int myField;
	public void method() {
	}
}

public class RenameCollisions {
	private String myDeclarationUsage;
	public String declarationUsage(String s) {
		return s + myDeclarationUsage;
	}

	public static void staticContext() {
		CASE_INSENSITIVE_ORDER.getClass();
		valueOf(0);

		String.CASE_INSENSITIVE_ORDER.getClass();
		String.valueOf(0);

		int var6 = LocalClass.STATIC_FIELD;
		LocalClass.staticMethod();

		LocalClass localClass = new LocalClass();
		localClass.myField++;
		localClass.method();
	}
}
