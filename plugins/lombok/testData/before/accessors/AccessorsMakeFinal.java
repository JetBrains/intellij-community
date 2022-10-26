class AccessorsMakeFinal {
	@lombok.Setter
  @lombok.experimental.Accessors(fluent = true, makeFinal = true)
	private String test;
}
