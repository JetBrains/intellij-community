package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Lists;
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
      if ("arg".equals(reader.getNodeName())) {
        signature.addArgument(readString(reader, "name", ""), readString(reader, "type", ""));
      }
      else if ("return".equals(reader.getNodeName())) {
        signature.addReturnType(readString(reader, "type", ""));
      }
      else {
        throw new PyDebuggerException("Expected <arg> or <return>, found " + reader.getNodeName());
      }

      reader.moveUp();
    }

    return signature;
  }

  public static PyConcurrencyEvent parseConcurrencyEvent(String payload,
                                                         final PyPositionConverter positionConverter) throws PyDebuggerException {
    final XppReader reader = openReader(payload, true);
    reader.moveDown();
    String eventName = reader.getNodeName();
    boolean isAsyncio;
    if (eventName.equals("threading_event")) {
      isAsyncio = false;
    }
    else if (eventName.equals("asyncio_event")) {
      isAsyncio = true;
    }
    else {
      throw new PyDebuggerException("Expected <threading_event> or <asyncio_event>, found " + reader.getNodeName());
    }

    final Long time = Long.parseLong(readString(reader, "time", ""));
    final String name = readString(reader, "name", "");
    final String thread_id = readString(reader, "thread_id", "");
    final String type = readString(reader, "type", "");
    PyConcurrencyEvent threadingEvent;
    if (type.equals("lock")) {
      String lock_id = readString(reader, "lock_id", "0");
      threadingEvent = new PyLockEvent(time, thread_id, name, lock_id, isAsyncio);
    }
    else if (type.equals("thread")) {
      String parentThread = readString(reader, "parent", "");
      if (!parentThread.isEmpty()) {
        threadingEvent = new PyThreadEvent(time, thread_id, name, parentThread, isAsyncio);
      }
      else {
        threadingEvent = new PyThreadEvent(time, thread_id, name, isAsyncio);
      }
    }
    else {
      throw new PyDebuggerException("Unknown type " + type);
    }

    final String eventType = readString(reader, "event", "");
    if (eventType.equals("__init__")) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.CREATE);
    }
    else if (eventType.equals("start")) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.START);
    }
    else if (eventType.equals("join")) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.JOIN);
    }
    else if (eventType.equals("stop")) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.STOP);
    }
    else if (eventType.equals("acquire_begin") || eventType.equals("__enter___begin")
             || (eventType.equals("get_begin")) || (eventType.equals("put_begin"))) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.ACQUIRE_BEGIN);
    }
    else if (eventType.equals("acquire_end") || eventType.equals("__enter___end")
             || (eventType.equals("get_end")) || (eventType.equals("put_end"))) {
      threadingEvent.setType(PyConcurrencyEvent.EventType.ACQUIRE_END);
    }
    else if (eventType.startsWith("release") || eventType.startsWith("__exit__")) {
      // we record release begin and end on the Python side, but it is not important info
      // for user. Maybe use it later
      threadingEvent.setType(PyConcurrencyEvent.EventType.RELEASE);
    }
    else {
      throw new PyDebuggerException("Unknown event " + eventType);
    }

    threadingEvent.setFileName(readString(reader, "file", ""));
    threadingEvent.setLine(Integer.parseInt(readString(reader, "line", "")) - 1);
    reader.moveUp();

    final List<PyStackFrameInfo> frames = new LinkedList<>();
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      frames.add(parseFrame(reader, thread_id, positionConverter));
      reader.moveUp();
    }
    threadingEvent.setFrames(frames);
    return threadingEvent;
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
    if ("None".equals(message) || message.isEmpty()) {
      message = null;
    }

    final List<PyStackFrameInfo> frames = new LinkedList<>();
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
  public static List<PyDebugValue> parseReferrers(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final List<PyDebugValue> values = new LinkedList<>();

    final XppReader reader = openReader(text, false);

    while (reader.hasMoreChildren()) {
      reader.moveDown();
      if (reader.getNodeName().equals("var")) {
        PyDebugValue value = parseValue(reader, frameAccessor);
        value.setId(readString(reader, "id", null));
        values.add(value);
      }
      else if (reader.getNodeName().equals("for")) {
        //TODO
      }
      else {
        throw new PyDebuggerException("Expected <var> or <for>, found " + reader.getNodeName());
      }
      reader.moveUp();
    }

    return values;
  }


  @NotNull
  public static List<PyDebugValue> parseValues(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final List<PyDebugValue> values = new LinkedList<>();

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
    final String qualifier = readString(reader, "qualifier", ""); //to be able to get the fully qualified type if necessary

    String value = readString(reader, "value", null);
    final String isContainer = readString(reader, "isContainer", "");
    final String isReturnedValue = readString(reader, "isRetVal", "");
    final String isErrorOnEval = readString(reader, "isErrorOnEval", "");

    if (value.startsWith(type + ": ")) {  // drop unneeded prefix
      value = value.substring(type.length() + 2);
    }

    return new PyDebugValue(name, type, qualifier, value, "True".equals(isContainer), "True".equals(isReturnedValue),
                            "True".equals(isErrorOnEval), frameAccessor);
  }

  public static ArrayChunk parseArrayValues(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final XppReader reader = openReader(text, false);
    ArrayChunkBuilder result = new ArrayChunkBuilder();
    if (reader.hasMoreChildren()) {
      reader.moveDown();
      if (!"array".equals(reader.getNodeName())) {
        throw new PyDebuggerException("Expected <array> at first node, found " + reader.getNodeName());
      }
      String slice = readString(reader, "slice", null);
      result.setSlicePresentation(slice);
      result.setRows(readInt(reader, "rows", null));
      result.setColumns(readInt(reader, "cols", null));
      result.setFormat("%" + readString(reader, "format", null));
      result.setType(readString(reader, "type", null));
      result.setMax(readString(reader, "max", null));
      result.setMin(readString(reader, "min", null));
      result.setValue(new PyDebugValue(slice, null, null, null, false, false, false, frameAccessor));
      reader.moveUp();
    }
    if ("headerdata".equals(reader.peekNextChild())) {
      parseArrayHeaderData(reader, result);
    }

    Object[][] data = parseArrayValues(reader, frameAccessor);
    result.setData(data);
    return result.createArrayChunk();
  }

  private static void parseArrayHeaderData(XppReader reader, ArrayChunkBuilder result) throws PyDebuggerException {
    List<String> rowHeaders = Lists.newArrayList();
    List<ArrayChunk.ColHeader> colHeaders = Lists.newArrayList();
    reader.moveDown();
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      if ("colheader".equals(reader.getNodeName())) {
        colHeaders.add(new ArrayChunk.ColHeader(
          readString(reader, "label", null),
          readString(reader, "type", null),
          readString(reader, "format", null),
          readString(reader, "max", null),
          readString(reader, "min", null)));
      }
      else if ("rowheader".equals(reader.getNodeName())) {
        rowHeaders.add(readString(reader, "label", null));
      }
      else {
        throw new PyDebuggerException("Invalid node name" + reader.getNodeName());
      }
      reader.moveUp();
    }
    result.setColHeaders(colHeaders);
    result.setRowLabels(rowHeaders);
    reader.moveUp();
  }

  public static Object[][] parseArrayValues(final XppReader reader, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    int rows = -1;
    int cols = -1;
    if (reader.hasMoreChildren()) {
      reader.moveDown();
      if (!"arraydata".equals(reader.getNodeName())) {
        throw new PyDebuggerException("Expected <arraydata> at second node, found " + reader.getNodeName());
      }
      rows = readInt(reader, "rows", null);
      cols = readInt(reader, "cols", null);
      reader.moveUp();
    }

    if (rows <= 0 || cols <= 0) {
      throw new PyDebuggerException("Array xml: bad rows or columns number: (" + rows + ", " + cols + ")");
    }
    Object[][] values = new Object[rows][cols];

    int currRow = 0;
    int currCol = 0;
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      if (!"var".equals(reader.getNodeName()) && !"row".equals(reader.getNodeName())) {
        throw new PyDebuggerException("Expected <var> or <row>, found " + reader.getNodeName());
      }
      if ("row".equals(reader.getNodeName())) {
        int index = readInt(reader, "index", null);
        if (currRow != index) {
          throw new PyDebuggerException("Array xml: expected " + currRow + " row, found " + index);
        }
        if (currRow > 0 && currCol != cols) {
          throw new PyDebuggerException("Array xml: expected " + cols + " filled columns, got " + currCol + " instead.");
        }
        currRow += 1;
        currCol = 0;
      }
      else {
        PyDebugValue value = parseValue(reader, frameAccessor);
        values[currRow - 1][currCol] = value.getValue();
        currCol += 1;
      }
      reader.moveUp();
    }

    return values;
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
