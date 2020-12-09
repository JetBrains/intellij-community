@lombok.Data @lombok.Getter(lombok.AccessLevel.NONE)
class DataWithGetterNone {
	private int x, y;
	private final String z;
}