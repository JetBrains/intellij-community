import org.hamcrest.Matchers;
import org.testng.annotations.Test;

public class SampleTest {

  @Test
  public void differentAssertions() {
    org.hamcrest.MatcherAssert.assertThat(1, Matchers.is(1));
    org.hamcrest.MatcherAssert.assertThat("reason", 1, Matchers.is(1));
  }
}