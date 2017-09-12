package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsWitherTest {

  private String makeResults(String fieldName, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.EMPTY;
    String lombokResult = LombokHandlerUtil.toWitherName(accessorsInfo, fieldName, isBoolean);
    String result = LombokUtils.toWitherName(accessorsInfo, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
  }

  @Test
  public void testToWitherNames_dValue() throws Exception {
    String result = makeResults("dValue", false);

    assertThat(result, equalTo("withDValue"));
  }

  @Test
  public void testToWitherNames_Value() throws Exception {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("withValue"));
  }

  @Test
  public void testToWitherNames_NonBoolean() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_NonBoolean_Uppercase() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_NonBoolean_Uppercase_Multiple() throws Exception {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("withMYField"));
  }

  @Test
  public void testToWitherNames_Boolean() throws Exception {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_Uppercase() throws Exception {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_is_Lowercase() throws Exception {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("withIsmyField"));
  }

  @Test
  public void testToWitherNames_Boolean_is_Uppercase() throws Exception {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("withMyField"));
  }

  @Test
  public void testToWitherNames_Boolean_IS() throws Exception {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("withISmyField"));
  }

}
