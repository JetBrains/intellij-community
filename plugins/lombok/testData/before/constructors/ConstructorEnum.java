@lombok.RequiredArgsConstructor
public enum ConstructorEnum {

	A(1), B(2);

	@lombok.Getter
	private final int x;
}