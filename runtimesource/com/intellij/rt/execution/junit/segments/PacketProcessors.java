package com.intellij.rt.execution.junit.segments;

/**
 * @author MYakovlev
 * Date: Feb 27, 2003
 * Time: 10:48:55 AM
 */
public class PacketProcessors{
  public static final PacketProcessor DEAF = new PacketProcessor() {
    public void processPacket(String packet) {
    }
  };
}
