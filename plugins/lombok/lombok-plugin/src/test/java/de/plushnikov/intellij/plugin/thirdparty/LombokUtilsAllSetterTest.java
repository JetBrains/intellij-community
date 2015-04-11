package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsAllSetterTest {

  private final List<String> lombokResult = new ArrayList<String>();
  private final List<String> result = new ArrayList<String>();

  private void makeResults(String fieldName, boolean isBoolean) {
    lombokResult.clear();
    result.clear();

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(false, false, false);
    lombokResult.addAll(LombokHandlerUtil.toAllSetterNames(accessorsInfo, fieldName, isBoolean));
    result.addAll(LombokUtils.toAllSetterNames(accessorsInfo, fieldName, isBoolean));

    assertThat(result, is(lombokResult));
  }

  @Test
  public void testToAllSetterNames_NonBoolean() throws Exception {
    makeResults("myField", false);

    assertThat(result, is(Arrays.asList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_NonBoolean_Uppercase() throws Exception {
    makeResults("myField", false);

    assertThat(result, is(Arrays.asList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_NonBoolean_Uppercase_Multiple() throws Exception {
    makeResults("MYField", false);

    assertThat(result, is(Arrays.asList("setMYField")));
  }

  @Test
  public void testToAllSetterNames_Boolean() throws Exception {
    makeResults("myField", true);

    assertThat(result, is(Arrays.asList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_Uppercase() throws Exception {
    makeResults("MyField", true);

    assertThat(result, is(Arrays.asList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_is_Lowercase() throws Exception {
    makeResults("ismyField", true);

    assertThat(result, is(Arrays.asList("setIsmyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_is_Uppercase() throws Exception {
    makeResults("isMyField", true);

    assertThat(result, is(Arrays.asList("setMyField", "setIsMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_IS() throws Exception {
    makeResults("ISmyField", true);

    assertThat(result, is(Arrays.asList("setISmyField")));
  }

}