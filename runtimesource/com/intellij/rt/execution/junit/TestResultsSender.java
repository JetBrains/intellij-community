package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.rt.junit4.Junit4TestMethodAdapter;
import com.intellij.rt.junit4.JUnit4Util;
import junit.framework.*;

public class TestResultsSender implements TestListener, TestSkippingListener {
  private OutputObjectRegistryImpl myRegistry;
  private PacketProcessor myErr;
  private final boolean isJunit4;
  private TestMeter myCurrentTestMeter;
  private Test myCurrentTest;

  public TestResultsSender(OutputObjectRegistryImpl packetFactory, PacketProcessor segmentedErr, final boolean isJunit4) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
    this.isJunit4 = isJunit4;
  }

  public synchronized void addError(Test test, Throwable throwable) {
    if (isJunit4 && JUnit4Util.isAssertion(throwable)) {
      // junit4 makes no distinction between errors and failures
      doAddFailure(test, (Error)throwable);
    }
    else if (throwable == Junit4TestMethodAdapter.IGNORED) {
      startTest(test);
      stopMeter(test);
      prepareIgnoredPacket(test, PoolOfTestStates.IGNORED_INDEX).send();
    }
    else {
      stopMeter(test);
      prepareDefectPacket(test, PoolOfTestStates.ERROR_INDEX, throwable).send();
    }
  }

  public synchronized void addFailure(Test test, AssertionFailedError assertion) {
    doAddFailure(test, assertion);
  }

  private void doAddFailure(final Test test, final Error assertion) {
    stopMeter(test);
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(Error assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion.getClass().getName().equals("org.junit.ComparisonFailure")) {
      return ComparisonDetailsExtractor.create(assertion);
    }
    return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
  }

  private Packet prepareDefectPacket(Test test, int state, Throwable assertion) {
    return myRegistry.createPacket().
            setTestState(test, state).
            addThrowable(assertion);
  }
  private Packet prepareIgnoredPacket(Test test, int state) {
    return myRegistry.createPacket().setTestState(test, state).addObject(test);
  }

  public synchronized void endTest(Test test) {
    stopMeter(test);
    Packet packet = myRegistry.createPacket().setTestState(test, PoolOfTestStates.COMPLETE_INDEX);
    myCurrentTestMeter.writeTo(packet);
    packet.send();
    myRegistry.forget(test);
  }

  private void stopMeter(Test test) {
    if (test != myCurrentTest) {
      myCurrentTestMeter = new TestMeter();
      //noinspection HardCodedStringLiteral
      System.err.println("Wrong test finished. Last started: " + myCurrentTest + " stopped: " + test);
    }
    myCurrentTestMeter.stop();
  }

  private void switchOutput(Packet switchPacket) {
    switchPacket.send();
    switchPacket.sendThrough(myErr);
  }

  public synchronized void startTest(Test test) {
    myCurrentTest = test;
    myRegistry.createPacket().setTestState(test, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(test));
    myCurrentTestMeter = new TestMeter();
  }

  public synchronized  void onTestSkipped(TestCase test, Test peformedTest) {
    myRegistry.createPacket().
        setTestState(test, PoolOfTestStates.SKIPPED_INDEX).
        addObject(peformedTest).
        send();
  }
}
