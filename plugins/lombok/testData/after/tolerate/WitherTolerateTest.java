public class WitherTolerateTest {
    private final Integer score;

    public WitherTolerateTest withScore(String score) {
        return withScore(Integer.parseInt(score));
    }

    public WitherTolerateTest(Integer score) {
        this.score = score;
    }

    public WitherTolerateTest withScore(Integer score) {
        return this.score == score ? this : new WitherTolerateTest(score);
    }
}
