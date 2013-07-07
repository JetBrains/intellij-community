class GetterOnMethod {
	@lombok.Getter(onMethod=@_(@Deprecated)) int i;
	@lombok.Getter(onMethod=@_({@java.lang.Deprecated, @Test})) int j, k;

	public @interface Test {
	}
}
