class SetterOnMethodOnParam {
	@lombok.Setter(onMethod=@_(@Deprecated)) int i;
	@lombok.Setter(onMethod=@_({@java.lang.Deprecated, @Test}), onParam=@_(@Test)) int j, k;

	public @interface Test {
	}
}
