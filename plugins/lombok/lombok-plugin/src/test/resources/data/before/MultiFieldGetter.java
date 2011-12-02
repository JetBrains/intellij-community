import lombok.AccessLevel;
import lombok.Getter;

class MultiFieldGetter {
  @Getter(AccessLevel.PROTECTED)
  int x, y;
}

@Getter
class MultiFieldGetter2 {
  @Getter(AccessLevel.PACKAGE)
  int x, y;
}