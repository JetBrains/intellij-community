package com.intellij.rt.junit4;

import com.intellij.rt.execution.junit.*;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class JUnit4TestResultsSender extends RunListener {
  private final OutputObjectRegistryEx myRegistry;
  private final PacketProcessor myErr;
  private TestMeter myCurrentTestMeter;
  private Description myCurrentTest;

  public JUnit4TestResultsSender(OutputObjectRegistryEx packetFactory, PacketProcessor segmentedErr) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
  }

  public synchronized void testFailure(Failure failure) throws Exception {
    final Description description = failure.getDescription();
    final Throwable throwable = failure.getException();

    if (throwable instanceof AssertionError) {
      // junit4 makes no distinction between errors and failures
      doAddFailure(description, (Error)throwable);
    }

    else {
      stopMeter(description);
      prepareDefectPacket(description, throwable).send();
    }
  }

  public synchronized void testIgnored(Description description) throws Exception {
    final Ignore ignoredAnnotation = (Ignore)description.getAnnotation(Ignore.class);
    final String val = ignoredAnnotation != null ? ignoredAnnotation.value() : null;
    testStarted(description);
      stopMeter(description);
      prepareIgnoredPacket(description, val).send();
  }

  private void doAddFailure(final Description test, final Throwable assertion) {
    stopMeter(test);
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(Throwable assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion.getClass().getName().equals("org.junit.ComparisonFailure")) {
      return ComparisonDetailsExtractor.create(assertion);
    }
    return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
  }

  private Packet prepareDefectPacket(Description test, Throwable assertion) {
    return myRegistry.createPacket().
            setTestState(test, PoolOfTestStates.ERROR_INDEX).
            addThrowable(assertion);
  }
  private Packet prepareIgnoredPacket(Description test, String val) {
    return myRegistry.createPacket().setTestState(test, PoolOfTestStates.IGNORED_INDEX).addObject(test).addLimitedString(val != null ? val : "");
  }

  public void testFinished(Description description) throws Exception {
    stopMeter(description);
    Packet packet = myRegistry.createPacket().setTestState(description, PoolOfTestStates.COMPLETE_INDEX);
    myCurrentTestMeter.writeTo(packet);
    packet.send();
    myRegistry.forget(description);
  }

  private void stopMeter(Description test) {
    if (!test.equals(myCurrentTest)) {
      myCurrentTestMeter = new TestMeter();
      //noinspection HardCodedStringLiteral
      System.err.println("Wrong test finished. Last started: " + myCurrentTest+" stopped: " + test+"; "+test.getClass());
    }
    myCurrentTestMeter.stop();
  }

  private void switchOutput(Packet switchPacket) {
    switchPacket.send();
    switchPacket.sendThrough(myErr);
  }


  public synchronized void testStarted(Description description) throws Exception {
    myCurrentTest = description;
    myRegistry.createPacket().setTestState(description, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(description));
    myCurrentTestMeter = new TestMeter();
  }

}
