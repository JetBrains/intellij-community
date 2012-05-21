package com.wrq.rearranger.util;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates constants used at the Rearranger DSL.
 * 
 * @author Denis Zhdanov
 * @since 5/17/12 12:57 PM
 */
public enum RearrangerTestDsl {
  
  NAME,
  MODIFIER,
  /** Field initializer type. */
  INITIALIZER,
  /** Method target type (e.g. constructor). */
  TARGET,
  TYPE,
  RETURN_TYPE("returnType"),
  SORT,
  COMMENT,
  GETTER_CRITERIA("getterCriteria"),
  SETTER_CRITERIA("setterCriteria"),
  
  // Attributes
  INVERT,
  CONDITION,
  ALL_SUBSEQUENT("allSubsequent"),
  ALL_PRECEDING("allPreceding"),
  SUBSEQUENT_RULES_TO_MATCH("subsequentRulesToMatch"),
  PRECEDING_RULES_TO_MATCH("precedingRulesToMatch"),
  BODY;

  @NotNull private final String myValue;
  
  RearrangerTestDsl() {
    myValue = toString().toLowerCase();
  }

  RearrangerTestDsl(@NotNull String value) {
    myValue = value;
  }

  /**
   * @return    string value used at the Rearranger DSL
   */
  @NotNull
  public String getValue() {
    return myValue;
  }
}
