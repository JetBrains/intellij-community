package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsWitherTest {

  private static String makeResults(String fieldName, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.DEFAULT;
    return LombokUtils.toWitherName(accessorsInfo, fieldName, isBoolean);
  }

  @Test
  public void testToWitherNames_dValue() {
    String result = makeResults("dValue", false);

    assertThat(result, equalTo("withDValue"));
  }

  @Test
  public void testToWitherNames_Value() {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("withValue"));
  }

  @Test
  public void testToWitherNames_NonBoolean() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_NonBoolean_Uppercase() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_NonBoolean_Uppercase_Multiple() {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("withMYField"));
  }

  @Test
  public void testToWitherNames_Boolean() {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_Uppercase() {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_is_Lowercase() {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("withIsmyField"));
  }

  @Test
  public void testToWitherNames_Boolean_is_Uppercase() {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_IS() {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("withISmyField"));
  }

}
