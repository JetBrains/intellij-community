@lombok.RequiredArgsConstructor
@lombok.experimental.FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@lombok.experimental.WithBy
public class WithByTypes<T> {
	int a;
	long b;
	short c;
	char d;
	byte e;
	double f;
	float g;
	boolean h;
	T i;

	public static void example() {
		new WithByTypes<String>(0, 0, (short) 0, ' ', (byte) 0, 0.0, 0.0F, true, "").withHBy(x -> !x).withFBy(x -> x + 0.5);
	}
}
