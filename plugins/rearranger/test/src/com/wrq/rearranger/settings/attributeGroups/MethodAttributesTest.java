package com.wrq.rearranger.settings.attributeGroups;

/**
 * Test toString() method for MethodAttributes.
 */

import junit.framework.TestCase;

public class MethodAttributesTest
  extends TestCase
{
  MethodAttributes ma;

  public void testToString() throws Exception {
    ma = new MethodAttributes();
    // test various combinations of min/max parameters, name, and return type.
    ma.getProtectionLevelAttributes().setPlPublic(true);
    // min = ?  max = ?  name = ?  return = ?
    setma(false, 0, false, 0, null, null, "public methods");
    // min = 1  max = ?  name = ?  return = ?
    setma(true, 0, false, 0, null, null, "public methods with at least 0 parameters");
    setma(true, 1, false, 0, null, null, "public methods with at least 1 parameter");
    setma(true, 2, false, 0, null, null, "public methods with at least 2 parameters");
    // min = ?  max = 1  name = ?  return = ?
    setma(false, 0, true, 0, null, null, "public methods with at most 0 parameters");
    setma(false, 0, true, 1, null, null, "public methods with at most 1 parameter");
    setma(false, 0, true, 2, null, null, "public methods with at most 2 parameters");
    // min = ?  max = ?  name = N  return = ?
    setma(false, 0, false, 0, "NAME", null, "public methods whose names match 'NAME'");
    // min = ?  max = ?  name = ?  return = R
    setma(false, 0, false, 0, null, "int", "public methods whose return types match 'int'");
    //
    // min = 1  max = 3  name = ?  return = ?
    setma(true, 0, true, 0, null, null, "public methods with 0 parameters");
    setma(true, 1, true, 1, null, null, "public methods with 1 parameter");
    setma(true, 2, true, 2, null, null, "public methods with 2 parameters");
    setma(true, 0, true, 2, null, null, "public methods with 0 to 2 parameters");
    // min = 1  max = 1  name = N  return = ?
    setma(true, 0, true, 2, "NAME", null, "public methods with 0 to 2 parameters, " +
                                          "and whose names match 'NAME'");
    // min = 1  max = 1  name = ?  return = R
    setma(true, 0, true, 2, null, "int", "public methods with 0 to 2 parameters, " +
                                         "and whose return types match 'int'");
    // min = 1  max = 1  name = N  return = R
    setma(true, 0, true, 2, "NAME", "int", "public methods with 0 to 2 parameters, " +
                                           "whose names match 'NAME', and whose return types match 'int'");
    ma.getMinParamsAttr().setMatch(true);
    ma.getMinParamsAttr().setValue(1);
    ma.getMinParamsAttr().setMatch(true);
    ma.getMinParamsAttr().setValue(1);
  }

  private void setma(boolean minMatch, int min, boolean maxMatch, int max, String name, String returnValue,
                     String description)
  {
    ma.getMinParamsAttr().setMatch(minMatch);
    ma.getMinParamsAttr().setValue(min);
    ma.getMaxParamsAttr().setMatch(maxMatch);
    ma.getMaxParamsAttr().setValue(max);
    ma.getNameAttribute().setMatch(name != null);
    ma.getNameAttribute().setExpression(name);
    ma.getReturnTypeAttr().setMatch(returnValue != null);
    ma.getReturnTypeAttr().setExpression(returnValue);
    assertEquals("wrong description", description, ma.toString());
  }
}