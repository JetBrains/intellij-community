package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit2.segments.Packet;

public class TestMeter {
  private long myStartTime;

  private long myInitialUsedMemory;
  private long myFinalMemory;
  private long myDuration;

  private boolean myIsStopped = false;


  public TestMeter() {
    myStartTime = System.currentTimeMillis();
    myInitialUsedMemory = usedMemory();
  }

  private long usedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  public void stop() {
    if (!myIsStopped) {
      myDuration = System.currentTimeMillis() - myStartTime;
      myFinalMemory = usedMemory();
      myIsStopped = true;
    }
  }

  public void writeTo(Packet packet) {
    packet.addLong(myDuration).
        addLong(myInitialUsedMemory).
        addLong(myFinalMemory);
  }
}
