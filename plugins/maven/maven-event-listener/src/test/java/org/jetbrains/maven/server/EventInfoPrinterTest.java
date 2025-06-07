// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventInfoPrinterTest {
  @Test
  public void testBasicPrinting() {
    assertPrintResult("[IJ]-1-null", null);
    assertPrintResult("[IJ]-1-type", "type");
    assertPrintResult("[IJ]-1-type-[IJ]-name=value", "type", "name", "value");
    assertPrintResult("[IJ]-1-type-[IJ]-name=null", "type", "name", null);
    assertPrintResult("[IJ]-1-type-[IJ]-name1=value1-[IJ]-name2=value2",
                      "type", "name1", "value1", "name2", "value2");
  }

  @Test
  public void testPrintingWithNewLines() {
    assertPrintResult("[IJ]-1-type-[IJ]-name=-[N]-", "type", "name", "\n");
    assertPrintResult("[IJ]-1-type-[IJ]-name=-[N]--[N]-", "type", "name", "\n\n");
    assertPrintResult("[IJ]-1-type-[IJ]-name=-[N]-abc", "type", "name", "\nabc");
    assertPrintResult("[IJ]-1-type-[IJ]-name=abc-[N]-", "type", "name", "abc\n");
    assertPrintResult("[IJ]-1-type-[IJ]-name=abc-[N]-def", "type", "name", "abc\ndef");
    assertPrintResult("[IJ]-1-type-[IJ]-name=-[N]-abc-[N]-", "type", "name", "\nabc\n");
    assertPrintResult("[IJ]-1-type-[IJ]-name=1-[N]-2-[N]-3-[N]--[N]-4-[N]--[N]-5-[N]--[N]-6",
                      "type", "name", "1\n2\n\r3\n\n4\n\r\n5\n\n\r6");
  }

  private static void assertPrintResult(String expected,
                                        Object type,
                                        CharSequence... objects) {
    assertEquals(expected, EventInfoPrinter.printToBuffer(type, objects).toString());

  }
}