import java.util.List;

class BuilderInstanceMethod<T> {
	@lombok.Builder
	public String create(int show, final int yes, List<T> also, int $andMe) {
		return "" + show + yes + also + $andMe;
	}
}
