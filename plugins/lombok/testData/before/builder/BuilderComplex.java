import java.util.List;
import lombok.Builder;

class BuilderComplex {
	@Builder(buildMethodName = "execute")
	private static <T extends Number> void testVoidWithGenerics(T number, int arg2, String arg3, BuilderComplex selfRef) {}
}
