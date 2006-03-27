package com.intellij.rt.ant.execution;

import com.intellij.rt.execution.junit.segments.PacketWriter;

/**
 * @author dyoma
 */
class PacketFactory {
  private int myLastMessageId = -1;
  public static final PacketFactory ourInstance = new PacketFactory();

  public synchronized PacketWriter createPacket(char id) {
    PacketWriter writer = new PacketWriter();
    myLastMessageId++;
    writer.appendLong(myLastMessageId);
    writer.appendChar(id);
    return writer;
  }
}
