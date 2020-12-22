class GetterLazy {
	static class ValueType {
	}
	
	@lombok.Getter(lazy=true)
	private final ValueType fieldName = new ValueType();
}
