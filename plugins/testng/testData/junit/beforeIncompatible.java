import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertThat;

public class Sample<caret>Test {

  @Test
  public void differentAssertions() {
    assertThat(1, Matchers.is(1));
    assertThat("reason", 1, Matchers.is(1));
  }
}