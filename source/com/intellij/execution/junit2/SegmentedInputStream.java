package com.intellij.execution.junit2;

import com.intellij.rt.execution.junit2.segments.Packet;
import com.intellij.rt.execution.junit2.segments.PacketProcessor;
import com.intellij.rt.execution.junit2.segments.SegmentedStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SegmentedInputStream extends InputStream implements SegmentedStream {
  private PushReader mySourceStream;
  private PacketProcessor myEventsDispatcher;
  private int myStartupPassed = 0;

  public SegmentedInputStream(final InputStream sourceStream) {
    mySourceStream = new PushReader(new BufferedReader(new InputStreamReader(sourceStream)));
  }

  public int read() throws IOException {
    if (myStartupPassed < STARTUP_MESSAGE.length()) {
      return rawRead();
    } else {
      final int result = findNextSymbol();
      return result;
    }
  }

  private int rawRead() throws IOException {
    while(myStartupPassed < STARTUP_MESSAGE.length()) {
      final int aChar = readNext();
      if (aChar != STARTUP_MESSAGE.charAt(myStartupPassed)) {
        mySourceStream.pushBack(aChar);
        mySourceStream.pushBack(STARTUP_MESSAGE.substring(0, myStartupPassed).toCharArray());
        myStartupPassed = 0;
        return readNext();
      }
      myStartupPassed++;
    }
    return read();
  }

  private int findNextSymbol() throws IOException {
    int nextByte;
    while (true) {
      nextByte = readNext();
      if (nextByte != SPECIAL_SYMBOL) break;
      final boolean packetRead = readControlSequence();
      if (!packetRead) break;
    }
    return nextByte;
  }

  private boolean readControlSequence() throws IOException {
    if (readNext() == SPECIAL_SYMBOL)
      return false;
    final char[] marker = readMarker();
    if(myEventsDispatcher != null) myEventsDispatcher.processPacket(decode(marker));
    return true;
  }

  public void setEventsDispatcher(final PacketProcessor eventsDispatcher) {
    myEventsDispatcher = eventsDispatcher;
  }

  private char[] readMarker() throws IOException {
    int nextRead = '0';
    final StringBuffer buffer = new StringBuffer();
    while (nextRead != ' ') {
      buffer.append((char)nextRead);
      nextRead = readNext();
    }
    return readNext(Integer.valueOf(buffer.toString()).intValue());
  }

  private char[] readNext(final int charCount) throws IOException {
    return mySourceStream.next(charCount);
  }

  private int readNext() throws IOException {
    return mySourceStream.next();
  }

  public int available() throws IOException {
    return mySourceStream.ready() ? 1 : 0;
  }

  public void close() throws IOException {
    mySourceStream.close();
  }

  public static String decode(final char[] chars) {
    final StringBuffer buffer = new StringBuffer(chars.length);
    for (int i = 0; i < chars.length; i++) {
      char chr = chars[i];
      final char decodedChar;
      if (chr == Packet.ourSpecialSymbol) {
        i++;
        chr = chars[i];
        if (chr != Packet.ourSpecialSymbol) {
          final StringBuffer codeBuffer = new StringBuffer(Packet.CODE_LENGTH);
          codeBuffer.append(chr);
          for (int j = 1; j < Packet.CODE_LENGTH; j++)
            codeBuffer.append(chars[i+j]);
          i += Packet.CODE_LENGTH - 1;
          decodedChar = (char)Integer.parseInt(codeBuffer.toString());
        }
        else decodedChar = chr;
      } else decodedChar = chr;
      buffer.append(decodedChar);
    }
    return buffer.toString();
  }
}
