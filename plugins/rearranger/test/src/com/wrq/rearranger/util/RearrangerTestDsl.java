package com.wrq.rearranger.util;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates constants used at the Rearranger DSL.
 * 
 * @author Denis Zhdanov
 * @since 5/17/12 12:57 PM
 */
public enum RearrangerTestDsl {

  // Settings
  EXTRACTED_METHODS("extracted methods"),
  OVERLOADED_METHODS("overloaded methods"),
  DEPTH_FIRST_ORDER("depth-first order"),
  ORDER,
  COMMENT_TYPE("commentType"),
  KEEP_TOGETHER("keep together"),
  OVERLOADED,
  GETTERS_SETTERS("getters and setters"),
  GETTERS_SETTERS_WITH_PROPERTY("getters and setters with property"),
  
  // Rules
  NAME,
  MODIFIER,
  /** Field initializer type. */
  INITIALIZER,
  /** Method target type (e.g. constructor). */
  TARGET,
  TYPE,
  RETURN_TYPE("return type"),
  SORT("sort by"),
  NOT_SORT("not sort by"),
  COMMENT,
  GETTER_CRITERIA("getter criteria"),
  SETTER_CRITERIA("setter criteria"),
  SPACING,
  PRECEDING_COMMENT("preceding comment"),
  TRAILING_COMMENT("trailing comment"),
  SETUP,
  GROUP_EXTRACTED_METHODS("group extracted methods"),
  ALPHABETIZE,
  PRIORITY,
  
  // Attributes
  INVERT,
  CONDITION,
  ALL_SUBSEQUENT("all subsequent"),
  ALL_PRECEDING("all preceding"),
  SUBSEQUENT_RULES_TO_MATCH("subsequent rules to match"),
  PRECEDING_RULES_TO_MATCH("preceding rules to match"),
  BODY,
  ANCHOR,
  BLANK_LINES("lines"),
  REMOVE_BLANK_LINES("remove blank lines"),
  BELOW_FIRST_CALLER("below first caller"),
  NON_PRIVATE_TREATMENT("non-private treatment"),
  REARRANGE_INNER_CLASSES("rearrange inner classes"),
  CLASS_COMMENT("class comment"),
  ARGUMENTS_NUMBER("arguments number"),
  FROM,
  TO,
  FILL_STRING("fill string"),
  USE_PROJECT_WIDTH_FOR_COMMENT_FILL("use project width for fill"),
  FILL_WIDTH("fill width"),
  ENUM;

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
