package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;


public class ProtocolFrame {

  private final int myCommand;
  private final int mySequence;
  private @NotNull final String myPayload;

  public ProtocolFrame(final int command, final int sequence, @NotNull final String payload) throws PyDebuggerException {
    myCommand = command;
    mySequence = sequence;
    myPayload = payload;
  }

  public ProtocolFrame(final String frame) throws PyDebuggerException {
    final String[] parts = frame.split("\t", 3);
    if (parts == null || parts.length < 2) {
      throw new PyDebuggerException("Bad frame: " + frame);
    }

    myCommand = Integer.parseInt(parts[0]);
    mySequence = Integer.parseInt(parts[1]);
    myPayload = (parts.length == 3 && !"".equals(parts[2]) ? ProtocolParser.decode(parts[2]) : "").trim();
  }

  public int getCommand() {
    return myCommand;
  }

  public int getSequence() {
    return mySequence;
  }

  @NotNull
  public String getPayload() {
    return myPayload;
  }

  @NotNull
  public byte[] pack() throws UnsupportedEncodingException {
    final StringBuilder sb = new StringBuilder();
    sb.append(Integer.toString(myCommand));
    sb.append('\t');
    sb.append(Integer.toString(mySequence));
    sb.append('\t');
    sb.append(myPayload);
    sb.append('\n');
    return sb.toString().getBytes("UTF-8");
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append('[');
    sb.append(Integer.toString(myCommand));
    sb.append(':');
    sb.append(Integer.toString(mySequence));
    sb.append(':');
    sb.append(myPayload);
    sb.append(']');
    return sb.toString();
  }

}
