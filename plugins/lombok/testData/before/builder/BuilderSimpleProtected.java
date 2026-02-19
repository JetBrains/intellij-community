import java.util.List;

@lombok.Builder(access = lombok.AccessLevel.PROTECTED)
class BuilderSimpleProtected<T> {
	private List<T> also;
}
