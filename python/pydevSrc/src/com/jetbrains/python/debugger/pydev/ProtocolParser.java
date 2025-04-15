// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.values.DataFrameDebugValue;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.XppReader;
import io.github.xstream.mxparser.MXParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public final class ProtocolParser {
  private ProtocolParser() {
  }

  public static final String DUMMY_RET_VAL = "_dummy_ret_val";
  public static final String DUMMY_IPYTHON_HIDDEN = "_dummy_ipython_val";
  public static final String DUMMY_SPECIAL_VAR = "_dummy_special_var";
  public static final Set<String> HIDDEN_TYPES = Set.of(DUMMY_RET_VAL, DUMMY_IPYTHON_HIDDEN, DUMMY_SPECIAL_VAR);

  public static PySignature parseCallSignature(String payload) throws PyDebuggerException {
    final XppReader reader = openReader(payload, true);
    reader.moveDown();
    if (!"call_signature".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <call_signature>, found " + reader.getNodeName());
    }
    String file = reader.getAttribute("file");
    if (file == null) file = "";
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

    final long time = Long.parseLong(readString(reader, "time", ""));
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

  public static boolean parseInputCommand(String payload) {
    return payload.equals("True");
  }

  public static Pair<Boolean, String> parseSetNextStatementCommand(String payload) throws PyDebuggerException {
    String[] values = payload.split("\t");
    if (values.length > 0) {
      boolean success = values[0].equals("True");
      String errorMessage = "Error";
      if (values.length > 1) {
        errorMessage = errorMessage + ": " + values[1];
      }
      return new Pair<>(success, errorMessage);
    }
    else {
      throw new PyDebuggerException("Unable to parse value: " + payload);
    }
  }

  public static String parseSourceContent(String payload) {
    return payload;
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

  public static @NotNull PyThreadInfo parseThread(final String text, final PyPositionConverter positionConverter) throws PyDebuggerException {
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

  public static @NotNull String getThreadId(@NotNull String payload) {
    return payload.split("\t")[0];
  }

  private static PyStackFrameInfo parseFrame(final XppReader reader, final String threadId, final PyPositionConverter positionConverter)
    throws PyDebuggerException {
    if (!"frame".equals(reader.getNodeName())) {
      throw new PyDebuggerException("Expected <frame>, found " + reader.getNodeName());
    }

    final String id = readString(reader, "id", null);
    final String name = readString(reader, "name", null);
    final String file = reader.getAttribute("file");
    final int line = readInt(reader, "line", 0);

    return new PyStackFrameInfo(threadId, id, name, positionConverter.convertPythonToFrame(file, line));
  }

  public static @NotNull PyDebugValue parseValue(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
    final XppReader reader = openReader(text, true);
    reader.moveDown();
    return parseValue(reader, frameAccessor);
  }

  public static @NotNull List<PyDebugValue> parseReferrers(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
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


  public static @NotNull List<PyDebugValue> parseValues(final String text, final PyFrameAccessor frameAccessor) throws PyDebuggerException {
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
    final String isErrorOnEval = readString(reader, "isErrorOnEval", "");
    if (HIDDEN_TYPES.contains(name)) {
      return new PyDebugValue(name, null, "", "", false, null, false,
                              false, "True".equals(isErrorOnEval), null, frameAccessor);
    }

    final String type = readString(reader, "type", null);
    final String qualifier = readString(reader, "qualifier", ""); //to be able to get the fully qualified type if necessary

    String value = readString(reader, "value", null);
    final String isContainer = readString(reader, "isContainer", "");
    final String isReturnedValue = readString(reader, "isRetVal", "");
    final String isIPythonHidden = readString(reader, "isIPythonHidden", "");
    String typeRendererId = readString(reader, "typeRendererId", "");
    String shape = readString(reader, "shape", "");

    if (value.startsWith(type + ": ")) {  // drop unneeded prefix
      value = value.substring(type.length() + 2);
    }
    if (shape.isEmpty()) shape = null;
    if (typeRendererId.isEmpty()) typeRendererId = null;
    if (type.equals(DataFrameDebugValue.pyDataFrameType)) {
      return new DataFrameDebugValue(name, type, qualifier, value, "True".equals(isContainer), shape, "True".equals(isReturnedValue),
                                     "True".equals(isIPythonHidden), "True".equals(isErrorOnEval), typeRendererId, frameAccessor);
    }
    return new PyDebugValue(name, type, qualifier, value, "True".equals(isContainer), shape, "True".equals(isReturnedValue),
                            "True".equals(isIPythonHidden), "True".equals(isErrorOnEval), typeRendererId, frameAccessor);
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
      result.setValue(new PyDebugValue(slice, null, null, null, false, null, false, false, false, null, frameAccessor));
      reader.moveUp();
    }
    if ("headerdata".equals(reader.peekNextChild())) {
      parseArrayHeaderData(reader, result);
    }

    Object[][] data = parseArrayValues(reader, frameAccessor);
    result.setData(data);
    return result.createArrayChunk();
  }

  public static @NotNull List<Pair<String, Boolean>> parseSmartStepIntoVariants(String text) throws PyDebuggerException {
    XppReader reader = openReader(text, false);
    List<Pair<String, Boolean>> variants = new ArrayList<>();
    while (reader.hasMoreChildren()) {
      reader.moveDown();
      String variantName = read(reader, "name", true);
      Boolean isVisited = read(reader, "isVisited", true).equals("true");
      variants.add(Pair.create(variantName, isVisited));
      reader.moveUp();
    }
    return variants;
  }

  private static void parseArrayHeaderData(XppReader reader, ArrayChunkBuilder result) throws PyDebuggerException {
    List<String> rowHeaders = new ArrayList<>();
    List<ArrayChunk.ColHeader> colHeaders = new ArrayList<>();
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
      return null;
      //throw new PyDebuggerException("Array xml: bad rows or columns number: (" + rows + ", " + cols + ")");
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

  public static String parseWarning(final String text) throws PyDebuggerException {
    final XppReader reader = openReader(text, true);
    reader.moveDown();
    return readString(reader, "id", null);
  }

  private static XppReader openReader(final String text, final boolean checkForContent) throws PyDebuggerException {
    final XppReader reader = new XppReader(new StringReader(text), new MXParser(), new NoNameCoder());
    if (checkForContent && !reader.hasMoreChildren()) {
      throw new PyDebuggerException("Empty frame: " + text);
    }
    return reader;
  }

  private static String readString(final XppReader reader, final String name, final String fallback) throws PyDebuggerException {
    final String value = read(reader, name, fallback == null);
    return value == null ? fallback : value;
  }

  private static int readInt(final XppReader reader, final String name, final Integer fallback) throws PyDebuggerException {
    final String value = read(reader, name, fallback == null);
    if (value == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      throw new PyDebuggerException("Unable to decode " + value + ": " + e.getMessage());
    }
  }

  @Contract("_, _, true -> !null")
  private static String read(final XppReader reader, final String name, boolean isRequired) throws PyDebuggerException {
    final String value = reader.getAttribute(name);
    if (value == null && isRequired) {
      throw new PyDebuggerException("Attribute not found: " + name);
    }
    return value == null ? null : URLUtil.decode(value);
  }
}
