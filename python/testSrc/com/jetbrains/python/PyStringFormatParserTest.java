package com.jetbrains.python;

import com.jetbrains.python.inspections.PyStringFormatParser;
import junit.framework.TestCase;

import java.util.List;

/**
 * @author yole
 */
public class PyStringFormatParserTest extends TestCase {
  public void testSimple() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("abc").parse();
    assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 3);
  }

  private static void assertConstant(PyStringFormatParser.FormatStringChunk aChunk, final int start, final int end) {
    PyStringFormatParser.ConstantChunk chunk = (PyStringFormatParser.ConstantChunk) aChunk;
    assertEquals(start, chunk.getStartIndex());
    assertEquals(end, chunk.getEndIndex());
  }

  public void testDoublePercent() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("abc%%def").parse();
    assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 8);
  }

  public void testFormat() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%s").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals(0, chunk.getStartIndex());
    assertEquals(2, chunk.getEndIndex());
    assertEquals('s', chunk.getConversionType());
  }

  public void testSubstitutionAfterFormat() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("Hello, %s").parse();
    assertEquals(2, chunks.size());
    assertConstant(chunks.get(0), 0, 7);
  }

  public void testMappingKey() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%(language)s").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals("language", chunk.getMappingKey());
    assertEquals('s', chunk.getConversionType());
  }

  public void testConversionFlags() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%#0d").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals("#0", chunk.getConversionFlags());
  }

  public void testWidth() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%345d").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals("345", chunk.getWidth());
  }

  public void testPrecision() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%.2d").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals("2", chunk.getPrecision());
  }

  public void testLengthModifier() {
    List<PyStringFormatParser.FormatStringChunk> chunks = new PyStringFormatParser("%ld").parse();
    assertEquals(1, chunks.size());
    PyStringFormatParser.SubstitutionChunk chunk = (PyStringFormatParser.SubstitutionChunk) chunks.get(0);
    assertEquals('l', chunk.getLengthModifier());
  }
}
