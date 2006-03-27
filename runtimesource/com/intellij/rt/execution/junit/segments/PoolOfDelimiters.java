package com.intellij.rt.execution.junit.segments;

/**
 * @noinspection HardCodedStringLiteral
 */
public interface PoolOfDelimiters {
  char REFERENCE_END = ':';
  char INTEGER_DELIMITER = ' ';

  String OBJECT_PREFIX = "O";
  String TREE_PREFIX = "T";
  String INPUT_COSUMER = "I";
  String CHANGE_STATE = "S";
  String TESTS_DONE = "D";
}
