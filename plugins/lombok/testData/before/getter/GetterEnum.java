import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access=AccessLevel.PRIVATE)
enum GetterEnum {
	ONE(1, "One")
	;
	@Getter private final int id;
	@Getter private final String name;
}
