import lombok.Getter;
import lombok.AccessLevel;

class MultiFieldGetter {
	@Getter(AccessLevel.PROTECTED) int x, y;
}

@Getter
class MultiFieldGetter2 {
	@Getter(AccessLevel.PACKAGE) int x, y;
}