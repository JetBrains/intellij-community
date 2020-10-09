import lombok.AccessLevel;
import lombok.Getter;

@Getter
class GetterNone {
	int i;
	@Getter(AccessLevel.NONE) int foo;
}