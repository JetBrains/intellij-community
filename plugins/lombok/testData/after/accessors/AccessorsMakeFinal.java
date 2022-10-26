class AccessorsMakeFinal {
	private String test;
	/**
	 * @return {@code this}.
	 */
	@SuppressWarnings("all")
	public final AccessorsMakeFinal test(final String test) {
		this.test = test;
		return this;
	}
}