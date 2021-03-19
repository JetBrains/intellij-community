import lombok.RequiredArgsConstructor;
import lombok.With;
import lombok.experimental.Tolerate;

@RequiredArgsConstructor
public class WitherTolerateTest {
    @With
    private final Integer score;

    @Tolerate
    public WitherTolerateTest withScore(String score) {
        return withScore(Integer.parseInt(score));
    }
}
