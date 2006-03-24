package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit2.segments.Packet;
import com.intellij.rt.execution.junit2.states.PoolOfTestStates;
import junit.framework.ComparisonFailure;
import junit.framework.Test;

import java.lang.reflect.Field;

/**
 * @noinspection HardCodedStringLiteral
 */
class ComparisonDetailsExtractor extends ExceptionPacketFactory {
  private static Field EXPECTED_FIELD = null;
  private static Field ACTUAL_FIELD = null;
  protected String myActual = "";
  protected String myExpected = "";

  static {
    try {
      Class exceptionClass = ComparisonFailure.class;
      exceptionClass.getDeclaredField("fExpected");
      EXPECTED_FIELD = exceptionClass.getDeclaredField("fExpected");
      EXPECTED_FIELD.setAccessible(true);
      ACTUAL_FIELD = exceptionClass.getDeclaredField("fActual");
      ACTUAL_FIELD.setAccessible(true);
    } catch (Throwable e) {}
  }

  public ComparisonDetailsExtractor(Throwable assertion, String expected, String actual) {
    super(PoolOfTestStates.COMPARISON_FAILURE, assertion);
    myActual = actual;
    myExpected = expected;
  }

  public static ExceptionPacketFactory create(AssertionError assertion) {
    try {
      String expected = assertion instanceof ComparisonFailure ? (String)EXPECTED_FIELD.get(assertion) : ((org.junit.ComparisonFailure)assertion).getExpected();
      String actual = assertion instanceof ComparisonFailure ? (String)ACTUAL_FIELD.get(assertion)  : ((org.junit.ComparisonFailure)assertion).getActual();
      return new ComparisonDetailsExtractor(assertion, expected, actual); }
    catch (Throwable e) {
      return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
    }
  }

  public Packet createPacket(OutputObjectRegistryImpl registry, Test test) {
    Packet packet = super.createPacket(registry, test);
    packet.
        addLimitedString(myExpected).
        addLimitedString(myActual);
    return packet;
  }
}
