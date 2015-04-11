package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsAllGetterTest {

  private static final AccessorsInfo DEFAULT_ACCESSORS = AccessorsInfo.build(false, false, false);
  private final List<String> lombokResult = new ArrayList<String>();
  private final List<String> result = new ArrayList<String>();

  private void makeResults(String fieldName, boolean isBoolean, AccessorsInfo accessorsInfo) {
    lombokResult.clear();
    result.clear();

    lombokResult.addAll(LombokHandlerUtil.toAllGetterNames(accessorsInfo, fieldName, isBoolean));
    result.addAll(LombokUtils.toAllGetterNames(accessorsInfo, fieldName, isBoolean));

    assertThat(result, is(lombokResult));
  }

  @Test
  public void testToAllGetterNames_NonBoolean() throws Exception {
    makeResults("myField", false, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getMyField")));
  }

  @Test
  public void testToAllGetterNames_NonBoolean_Uppercase() throws Exception {
    makeResults("myField", false, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getMyField")));
  }

  @Test
  public void testToAllGetterNames_NonBoolean_Uppercase_Multiple() throws Exception {
    makeResults("MYField", false, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getMYField")));
  }

  @Test
  public void testToAllGetterNames_Boolean() throws Exception {
    makeResults("myField", true, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_Uppercase() throws Exception {
    makeResults("MyField", true, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_is_Lowercase() throws Exception {
    makeResults("ismyField", true, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("isIsmyField", "getIsmyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_is_Uppercase() throws Exception {
    makeResults("isMyField", true, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("isIsMyField", "getIsMyField", "getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_IS() throws Exception {
    makeResults("ISmyField", true, DEFAULT_ACCESSORS);

    assertThat(result, is(Arrays.asList("getISmyField", "isISmyField")));
  }


  @Test
  public void testToAllGetterNames_NonBoolean_Fluent() throws Exception {
    makeResults("myField", false, AccessorsInfo.build(true, false, false));

    assertThat(result, is(Arrays.asList("myField")));
  }
}