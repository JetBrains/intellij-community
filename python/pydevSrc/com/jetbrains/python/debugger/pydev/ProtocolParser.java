package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.debugger.*;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.XppReader;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.mxp1.MXParser;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;


public class ProtocolParser {

  private ProtocolParser() {
  }

  public static PySignature parseCallSignature(String payload) throws PyDebuggerException {
    final XppReader reader = openReader(payload, true);
    reader.moveDown();
    if (!"call_signature".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <call_signature>, found " + reader.getNodeName());
    }
    final String file = readString(reader, "file", "");
    final String name = readString(reader, "name", "");
    PySignature signature = new PySignature(file, name);

    while (reader.hasMoreChildren()) {
      reader.moveDown();
      if (!"arg".equals(reader.getNodeName())) {
        throw new PyDebuggerException("Expected <arg>, found " + reader.getNodeName());
      }
      signature.addArgument(readString(reader, "name", ""), readString(reader, "type", ""));
      reader.moveUp();
    }

    return signature;
  }

  public static String parseSourceContent(String payload) throws PyDebuggerException {
    return payload;
  }

  public static String decode(final String value) throws PyDebuggerException {
    try {
      return URLDecoder.decode(value, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new PyDebuggerException("Unable to decode: " + value + ", reason: " + e.getMessage());
    }
  }

  public static String encodeExpression(final String expression) {
    return StringUtil.replace(expression, "\n", "@LINE@");
  }

  public static PyIo parseIo(final String text) throws PyDebuggerException {
    final XppReader reader = openReader(text, true);
    reader.moveDown();
    if (!"io".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <io>, found " + reader.getNodeName());
    }
    final String s = readString(reader, "s", "");
    final int ctx = readInt(reader, "ctx", 1);
    return new PyIo(s, ctx);
  }

  @NotNull
  public static PyThreadInfo parseThread(final String text, final PyPositionConverter positionConverter) throws PyDebuggerException {
    final XppReader reader = openReader(text, true);
    reader.moveDown();
    if (!"thread".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <thread>, found " + reader.getNodeName());
    }

    final String id = readString(reader, "id", null);
    final String name = readString(reader, "name", "");
    final int stopReason = readInt(reader, "stop_reason", 0);
    String message = readString(reader, "message", "None");
    if ("None".equals(message)) {
      message = null;
    }

    final List<PyStackFrameInfo> frames = new LinkedList<PyStackFrameInfo>();
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      frames.add(parseFrame(reader, id, positionConverter));
      reader.moveUp();
    }

    return new PyThreadInfo(id, name, frames, stopReason, message);
  }

  @NotNull
  public static String getThreadId(@NotNull String payload) {
    return payload.split("\t")[0];
  }

  private static PyStackFrameInfo parseFrame(final XppReader reader, final String threadId, final PyPositionConverter positionConverter)
    throws PyDebuggerException {
    if (!"frame".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <frame>, found " + reader.getNodeName());
    }

    final String id = readString(reader, "id", null);
    final String name = readString(reader, "name", null);
    final String file = readString(reader, "file", null);
    final int line = readInt(reader, "line", 0);

    return new PyStackFrameInfo(threadId, id, name, positionConverter.create(file, line));
  }

  @NotNull
  public static PyDebugValue parseValue(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final XppReader reader = openReader(text, true);
    reader.moveDown();
    return parseValue(reader, frameAccessor);
  }

  @NotNull
  public static List<PyDebugValue> parseValues(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final List<PyDebugValue> values = new LinkedList<PyDebugValue>();

    final XppReader reader = openReader(text, false);
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      values.add(parseValue(reader, frameAccessor));
      reader.moveUp();
    }

    return values;
  }

  private static PyDebugValue parseValue(final XppReader reader, PyFrameAccessor frameAccessor) throws PyDebuggerException {
    if (!"var".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <var>, found " + reader.getNodeName());
    }

    final String name = readString(reader, "name", null);
    final String type = readString(reader, "type", null);
    String value = readString(reader, "value", null);
    final String isContainer = readString(reader, "isContainer", "");
    final String isErrorOnEval = readString(reader, "isErrorOnEval", "");

    if (value.startsWith(type + ": ")) {  // drop unneeded prefix
      value = value.substring(type.length() + 2);
    }

    return new PyDebugValue(name, type, value, "True".equals(isContainer), "True".equals(isErrorOnEval), frameAccessor);
  }

  private static XppReader openReader(final String text, final boolean checkForContent) throws PyDebuggerException {
    final XppReader reader = new XppReader(new StringReader(text), new MXParser(), new NoNameCoder());
    if (checkForContent && !reader.hasMoreChildren()) {
      throw new PyDebuggerException("Empty frame: " + text);
    }
    return reader;
  }

  private static String readString(final XppReader reader, final String name, final String fallback) throws PyDebuggerException {
    final String value;
    try {
      value = read(reader, name);
    }
    catch (PyDebuggerException e) {
      if (fallback != null) {
        return fallback;
      }
      else {
        throw e;
      }
    }
    return decode(value);
  }

  private static int readInt(final XppReader reader, final String name, final Integer fallback) throws PyDebuggerException {
    final String value;
    try {
      value = read(reader, name);
    }
    catch (PyDebuggerException e) {
      if (fallback != null) {
        return fallback;
      }
      else {
        throw e;
      }
    }
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      throw new PyDebuggerException("Unable to decode " + value + ": " + e.getMessage());
    }
  }

  private static String read(final XppReader reader, final String name) throws PyDebuggerException {
    final String value = reader.getAttribute(name);
    if (value == null) {
      throw new PyDebuggerException("Attribute not found: " + name);
    }
    return value;
  }
}
