class Types {
	public static Object <caret>referenceMethod(Object o) {
		return o.toString();
	}
	public static void referenceContext(Object o) {
		String s = o.getClass().toString();
		Object v = o.toString();
	}
}
