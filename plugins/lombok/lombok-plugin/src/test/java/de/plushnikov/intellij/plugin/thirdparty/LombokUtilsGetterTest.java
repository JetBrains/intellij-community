package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsGetterTest {

  private static final AccessorsInfo DEFAULT_ACCESSORS = AccessorsInfo.build(false, false, false);

  private String makeResults(String fieldName, boolean isBoolean) {
    String lombokResult = LombokHandlerUtil.toGetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);
    String result = LombokUtils.toGetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
  }

  @Test
  public void testToGetterNames_NonBoolean() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("getMyField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("getMyField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase_Multiple() throws Exception {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("getMYField"));
  }

  @Test
  public void testToGetterNames_Boolean() throws Exception {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_Uppercase() throws Exception {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Lowercase() throws Exception {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("isIsmyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Uppercase() throws Exception {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_IS() throws Exception {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("isISmyField"));
  }

}