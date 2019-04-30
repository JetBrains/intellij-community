package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;


public class ProtocolFrame {

  private final int myCommand;
  private final int mySequence;
  private @NotNull final String myPayload;

  public ProtocolFrame(final int command, final int sequence, @NotNull final String payload) {
    myCommand = command;
    mySequence = sequence;
    myPayload = payload;
  }

  public ProtocolFrame(final String frame) throws PyDebuggerException {
    final String[] parts = frame.split("\t", 3);
    if (parts.length < 2) {
      throw new PyDebuggerException("Bad frame: " + frame);
    }

    myCommand = Integer.parseInt(parts[0]);
    mySequence = Integer.parseInt(parts[1]);
    myPayload = (parts.length == 3 && !parts[2].isEmpty() ? ProtocolParser.decode(parts[2]) : "").trim();
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
  public byte[] pack() {
    String s = String.valueOf(myCommand) +
                '\t' +
                mySequence +
                '\t' +
                myPayload +
                '\n';
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "[" +
           myCommand +
           ':' +
           mySequence +
           ':' +
           myPayload +
           ']';
  }

}
