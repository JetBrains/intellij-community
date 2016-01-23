@lombok.experimental.Builder
class BuilderSimple<T> {
	private final int noshow = 0;
	private final int yes;
	private java.util.List<T> also;
	private int $butNotMe;
}
