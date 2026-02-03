import java.util.List;
public class WithByNullAnnos {
	final List<String> test;
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByNullAnnos(final List<String> test) {
		this.test = test;
	}
	@org.checkerframework.checker.nullness.qual.NonNull
	@java.lang.SuppressWarnings("all")
	@lombok.Generated
	public WithByNullAnnos withTestBy(final java.util.function.@org.checkerframework.checker.nullness.qual.NonNull Function<? super List<String>, ? extends List<String>> transformer) {
		return new WithByNullAnnos(transformer.apply(this.test));
	}
}
