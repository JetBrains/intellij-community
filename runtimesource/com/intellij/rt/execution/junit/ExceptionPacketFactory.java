package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;

public class ExceptionPacketFactory implements PacketFactory {
  private final Throwable myAssertion;
  private int myState;

  public ExceptionPacketFactory(int state, Throwable assertion) {
    myState = state;
    myAssertion = assertion;
  }

  public Packet createPacket(OutputObjectRegistryEx registry, Object test) {
    return registry.createPacket().
        setTestState(test, myState).
        addThrowable(myAssertion);
  }

  protected void setState(int state) { myState = state; }
}
