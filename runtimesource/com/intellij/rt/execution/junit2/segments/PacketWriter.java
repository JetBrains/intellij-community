package com.intellij.rt.execution.junit2.segments;

public class PacketWriter implements PoolOfDelimiters {
  private StringBuffer myBody = new StringBuffer();

  public void appendString(String string) {
    myBody.append(string);
  }

  public void appendLong(long integer) {
    myBody.append(integer);
    myBody.append(INTEGER_DELIMITER);
  }

  public void appendLimitedString(String message) {
    if (message == null)
      appendLimitedString("");
    else {
      appendLong(message.length());
      appendString(message);
    }
  }

  public String getString() {
    return myBody.toString();
  }

  public void sendThrough(PacketProcessor transport) {
    transport.processPacket(getString());
  }

  public void appendChar(char aChar) {
    myBody.append(aChar);
  }
}
