package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsWitherTest {
  private static final AccessorsInfo DEFAULT_ACCESSORS = AccessorsInfo.build(false, false, false);

  private String makeResults(String fieldName, boolean isBoolean) {
    String lombokResult = LombokHandlerUtil.toWitherName(DEFAULT_ACCESSORS, fieldName, isBoolean);
    String result = LombokUtils.toWitherName(DEFAULT_ACCESSORS, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
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