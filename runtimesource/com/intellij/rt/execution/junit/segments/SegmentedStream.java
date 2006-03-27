package com.intellij.rt.execution.junit.segments;

/**
 * @noinspection HardCodedStringLiteral
 */
public interface SegmentedStream {
  char SPECIAL_SYMBOL = '/';
  String SPECIAL_SYMBOL_STRING = String.valueOf(SPECIAL_SYMBOL);
  String MARKER_PREFIX = SPECIAL_SYMBOL_STRING + "M";
  String LENGTH_DELIMITER = " ";
  String STARTUP_MESSAGE = "@#IJIDEA#JUnitSupport#@";
}
