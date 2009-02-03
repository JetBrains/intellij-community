package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.Test;

public class ExceptionPacketFactory implements PacketFactory {
  private final Throwable myAssertion;
  private int myState;

  public ExceptionPacketFactory(int state, Throwable assertion) {
    myState = state;
    myAssertion = assertion;
  }

  public Packet createPacket(OutputObjectRegistryImpl registry, Test test) {
    return registry.createPacket().
        setTestState(test, myState).
        addThrowable(myAssertion);
  }

  protected void setState(int state) { myState = state; }
}
