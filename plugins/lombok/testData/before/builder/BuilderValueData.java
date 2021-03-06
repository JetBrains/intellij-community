import java.util.List;

@lombok.Builder @lombok.Value
//added "final" do lombok class
final class BuilderAndValue {
	private final int zero = 0;
}

@lombok.Builder @lombok.Data
class BuilderAndData {
	private final int zero = 0;
}
