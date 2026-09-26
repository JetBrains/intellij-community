public record WithByOnRecordComponent(String a, String b) {
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByOnRecordComponent withABy(final java.util.function.Function<? super String, ? extends String> transformer) {
		return new WithByOnRecordComponent(transformer.apply(this.a), this.b);
	}
}
