package com.intellij.rt.execution.junit2.segments;

import junit.framework.Test;
import junit.runner.BaseTestRunner;

import java.io.*;
import java.util.Vector;

public class Packet extends PacketWriter implements PoolOfKnownObjects {
  private OutputObjectRegistry myRegistry;
  private PacketProcessor myTransport;
  public static final char ourSpecialSymbol = '$';
  public static final char[] ourSymbolsToEncode = new char[] {'\n', '\r', SegmentedStream.SPECIAL_SYMBOL};
  public static final int CODE_LENGTH = 2;

  public Packet(PacketProcessor transport, OutputObjectRegistry registry) {
    myTransport = transport;
    myRegistry = registry;
  }

  public Packet addObject(Test test) {
    return addReference(myRegistry.referenceTo(test));
  }

  public Packet addReference(String reference) {
    appendString(reference + REFERENCE_END);
    return this;
  }

  public Packet switchInputTo(Test test) {
    appendString(INPUT_COSUMER);
    return addObject(test);
  }

  public Packet addString(String string) {
    appendString(string);
    return this;
  }

  public void send() {
    sendThrough(myTransport);
  }

  public Packet addLong(long integer) {
    appendLong(integer);
    return this;
  }

  public Packet setTestState(Test test, int state) {
    return addString(CHANGE_STATE).addObject(test).addLong(state);
  }

  public Packet addLimitedString(String message) {
    appendLimitedString(message);
    return this;
  }

  public Packet addThrowable(Throwable throwable) {
    String filteredTrace = BaseTestRunner.getFilteredTrace(throwable);
    String message = makeNewLinesCompatibleWithJUnit(throwable.toString());
    addLimitedString(message);
    if (filteredTrace.startsWith(message))
      filteredTrace = filteredTrace.substring(message.length());
    addLimitedString(new TraceFilter(filteredTrace).execute());
    return this;
  }

  private static String makeNewLinesCompatibleWithJUnit(String string) {
    try {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      BufferedReader reader = new BufferedReader(new StringReader(string));
      String line;
      while ((line = reader.readLine()) != null)
        writer.println(line);
      return buffer.getBuffer().toString();
    } catch (IOException e) {return null;}
  }

  public static String encode(String packet) {
    StringBuffer buffer = new StringBuffer(packet.length());
    for (int i = 0; i < packet.length(); i++) {
      char chr = packet.charAt(i);
      if (chr == ourSpecialSymbol) {
        buffer.append(chr);
        buffer.append(chr);
        continue;
      }
      boolean appendChar = true;
      for (int j = 0; j < ourSymbolsToEncode.length; j++)
        if (ourSymbolsToEncode[j] == chr) {
          buffer.append(ourSpecialSymbol);
          String code = String.valueOf((int)chr);
          while (code.length() < CODE_LENGTH)
            code = "0" + code;
          buffer.append(code);
          appendChar = false;
          break;
        }
      if (appendChar)
        buffer.append(chr);
    }
    return buffer.toString();
  }

  public Packet addStrings(Vector vector) {
    int size = vector.size();
    addLong(size);
    for (int i = 0; i < size; i++) {
      addLimitedString((String)vector.elementAt(i));
    }
    return this;
  }
}
