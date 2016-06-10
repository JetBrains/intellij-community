class GetterLazyInvalidNotFinal {
	private String fieldName = "";
}
class GetterLazyInvalidNotPrivate {
	final String fieldName = "";
}
class GetterLazyInvalidNotPrivateFinal {
	String fieldName = "";
}
class GetterLazyInvalidNone {
	private final String fieldName = "";
}
class GetterLazyInvalidClass {
	private final String fieldName = "";
	@java.lang.SuppressWarnings("all")
	public String getFieldName() {
		return this.fieldName;
	}
}
class GetterLazyInvalidNoInit {
	private final String fieldName;
	GetterLazyInvalidNoInit() {
		this.fieldName = "foo";
	}
}