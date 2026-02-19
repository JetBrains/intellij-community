class GetterOnMethodErrors2 {
	@lombok.Getter(onMethod=@_A_(@Deprecated)) private int bad1;
	@lombok.Getter(onMethod=@_(5)) private int bad2;
	@lombok.Getter(onMethod=@_({@Deprecated, 5})) private int bad3;
	@lombok.Getter(onMethod=@_(bar=@Deprecated)) private int bad4;
	@lombok.Getter(onMethod=@_) private int good1;
	@lombok.Getter(onMethod=@_()) private int good2;
	@lombok.Getter(onMethod=@_(value=@Deprecated)) private int good3;
	@lombok.Getter(onMethod=@_(value={@Deprecated, @Test})) private int good4;
	public @interface Test {
	}
}
