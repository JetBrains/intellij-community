class GetterOnMethodErrors2 {
	private int bad1;
	private int bad2;
	private int bad3;
	private int bad4;
	private int good1;
	private int good2;
	private int good3;
	private int good4;
	public @interface Test {
	}
	@java.lang.SuppressWarnings("all")
	public int getBad1() {
		return this.bad1;
	}
	@java.lang.SuppressWarnings("all")
	public int getBad2() {
		return this.bad2;
	}
	@Deprecated
	@java.lang.SuppressWarnings("all")
	public int getBad3() {
		return this.bad3;
	}
	@java.lang.SuppressWarnings("all")
	public int getBad4() {
		return this.bad4;
	}
	@java.lang.SuppressWarnings("all")
	public int getGood1() {
		return this.good1;
	}
	@java.lang.SuppressWarnings("all")
	public int getGood2() {
		return this.good2;
	}
	@Deprecated
	@java.lang.SuppressWarnings("all")
	public int getGood3() {
		return this.good3;
	}
	@Deprecated
	@Test
	@java.lang.SuppressWarnings("all")
	public int getGood4() {
		return this.good4;
	}
}
