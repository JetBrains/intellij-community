import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@Builder
public class BuilderWithXArgsConstructor {
    private int someProperty;
}
