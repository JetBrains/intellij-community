@lombok.Getter
@lombok.ToString
@lombok.RequiredArgsConstructor
public enum DataOnEnum {
	A("hello");
	private final String someField;
}