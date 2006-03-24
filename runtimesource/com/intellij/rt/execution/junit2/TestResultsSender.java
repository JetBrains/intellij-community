package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit2.segments.Packet;
import com.intellij.rt.execution.junit2.segments.PacketProcessor;
import com.intellij.rt.execution.junit2.states.PoolOfTestStates;
import com.intellij.rt.execution.junit.Junit4TestMethodAdapter;
import junit.framework.*;

public class TestResultsSender implements TestListener, TestSkippingListener {
  private OutputObjectRegistryImpl myRegistry;
  private PacketProcessor myErr;
  private TestMeter myCurrentTestMeter;
  private Test myCurrentTest;

  public TestResultsSender(OutputObjectRegistryImpl packetFactory, PacketProcessor segmentedErr) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
  }

  public synchronized void addError(Test test, Throwable throwable) {
    if (throwable instanceof AssertionError) {
      // junit4 makes no distinction between errors and failures
      doAddFailure(test, (AssertionError)throwable);
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

  private void doAddFailure(final Test test, final AssertionError assertion) {
    stopMeter(test);
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(AssertionError assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion instanceof org.junit.ComparisonFailure) {
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
