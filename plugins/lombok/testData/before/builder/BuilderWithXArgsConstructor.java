import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Builder;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@Builder
public class BuilderWithXArgsConstructor {
    private int someProperty;
}

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class BuilderWithAllArgsConstructorPrivate {
  private int someProperty;
}

@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
@Builder
class BuilderWithReqArgsConstructor {
  private final int someProperty;
}

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class BuilderWithReqArgsConstructorPrivate {
  private final int someProperty;
}
