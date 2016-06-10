class GetterLazyNative {
	@lombok.Getter(lazy=true)
	private final boolean booleanField = true;
	
	@lombok.Getter(lazy=true)
	private final byte byteField = 1;
	
	@lombok.Getter(lazy=true)
	private final short shortField = 1;
	
	@lombok.Getter(lazy=true)
	private final int intField = 1;
	
	@lombok.Getter(lazy=true)
	private final long longField = 1;
	
	@lombok.Getter(lazy=true)
	private final float floatField = 1.0f;
	
	@lombok.Getter(lazy=true)
	private final double doubleField = 1.0;
	
	@lombok.Getter(lazy=true)
	private final char charField = '1';
	
	@lombok.Getter(lazy=true)
	private final int[] intArrayField = new int[] {1};
}
