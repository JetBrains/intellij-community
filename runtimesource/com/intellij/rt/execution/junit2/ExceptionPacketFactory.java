package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit2.segments.Packet;
import junit.framework.Test;

public class ExceptionPacketFactory implements PacketFactory {
  private Throwable myAssertion;
  private int myState;

  public ExceptionPacketFactory(int state, Throwable assertion) {
    myState = state;
    myAssertion = assertion;
  }

  public Packet createPacket(OutputObjectRegistryImpl registry, Test test) {
    Packet packet = registry.createPacket().
        setTestState(test, myState).
        addThrowable(myAssertion);
    return packet;
  }

  protected void setState(int state) { myState = state; }
}
