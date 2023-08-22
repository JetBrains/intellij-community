// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.parsing;

import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNewStyleStringFormatParser;
import com.jetbrains.python.PyNewStyleStringFormatParser.Field;
import com.jetbrains.python.PyNewStyleStringFormatParser.ParseResult;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.assertOrderedEquals;
import static com.jetbrains.python.PyStringFormatParser.*;


public class PyStringFormatParserTest extends TestCase {
  public void testSimple() {
    List<FormatStringChunk> chunks = parsePercentFormat("abc");
    TestCase.assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 3);
  }

  private static void assertConstant(FormatStringChunk aChunk, final int start, final int end) {
    ConstantChunk chunk = (ConstantChunk)aChunk;
    assertEquals(start, chunk.getStartIndex());
    assertEquals(end, chunk.getEndIndex());
  }

  public void testDoublePercent() {
    List<FormatStringChunk> chunks = parsePercentFormat("abc%%def");
    TestCase.assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 8);
  }

  public void testFormat() {
    List<FormatStringChunk> chunks = parsePercentFormat("%s");
    TestCase.assertEquals(1, chunks.size());
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals(0, chunk.getStartIndex());
    assertEquals(2, chunk.getEndIndex());
    assertEquals('s', chunk.getConversionType());
  }

  public void testSubstitutionAfterFormat() {
    List<FormatStringChunk> chunks = parsePercentFormat("Hello, %s");
    TestCase.assertEquals(2, chunks.size());
    assertConstant(chunks.get(0), 0, 7);
  }

  public void testMappingKey() {
    List<FormatStringChunk> chunks = parsePercentFormat("%(language)s");
    TestCase.assertEquals(1, chunks.size());
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals("language", chunk.getMappingKey());
    assertEquals('s', chunk.getConversionType());
  }

  public void testConversionFlags() {
    List<FormatStringChunk> chunks = parsePercentFormat("%#0d");
    TestCase.assertEquals(1, chunks.size());
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals("#0", chunk.getConversionFlags());
  }

  public void testWidth() {
    List<FormatStringChunk> chunks = parsePercentFormat("%345d");
    TestCase.assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk)chunks.get(0);
    assertEquals("345", chunk.getWidth());
  }

  public void testPrecision() {
    List<FormatStringChunk> chunks = parsePercentFormat("%.2d");
    TestCase.assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk)chunks.get(0);
    assertEquals("2", chunk.getPrecision());
  }

  public void testLengthModifier() {
    List<FormatStringChunk> chunks = parsePercentFormat("%ld");
    TestCase.assertEquals(1, chunks.size());
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals('l', chunk.getLengthModifier());
  }

  public void testDoubleAsterisk() {
    List<FormatStringChunk> chunks = parsePercentFormat("%**d");
    TestCase.assertEquals(2, chunks.size());
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals(2, chunk.getEndIndex());
    assertEquals('\0', chunk.getConversionType());
  }

  public void testUnclosedMapping() {
    List<FormatStringChunk> chunks = parsePercentFormat("%(name1s");
    PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals("name1s", chunk.getMappingKey());
    assertTrue(chunk.isUnclosedMapping());
  }

  // PY-8372
  public void testNewStyleAutomaticNumbering() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("{}, {}"));
    TestCase.assertEquals(2, chunks.size());
    assertEquals(TextRange.create(0, 2), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(4, 6), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testNewStylePositionalArgs() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("{1}, {0}"));
    TestCase.assertEquals(2, chunks.size());
    assertEquals(TextRange.create(0, 3), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(5, 8), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testNewStyleKeywordArgs() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("a{foo}{bar}bc"));
    TestCase.assertEquals(2, chunks.size());
    assertEquals(TextRange.create(1, 6), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(6, 11), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testBracesEscaping() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("\\{\\}, {{}}"));
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(1, 4), chunks.get(0).getTextRange());
  }

  public void testAutoPosition() {
    final List<SubstitutionChunk> oldStyleChunks = filterSubstitutions(parsePercentFormat("%s %(foo)s %(bar)s %*.*d"));
    assertOrderedEquals(ContainerUtil.map(oldStyleChunks, SubstitutionChunk::getAutoPosition), 0, null, null, 1);

    final List<SubstitutionChunk> newStyleChunks = filterSubstitutions(parseNewStyleFormat("'{foo} {} {bar} {0} {}'"));
    assertOrderedEquals(ContainerUtil.map(newStyleChunks, SubstitutionChunk::getAutoPosition), null, 0, null, null, 1);
  }

  public void testNewStyleConstant() {
    List<FormatStringChunk> chunks = parseNewStyleFormat("a");
    TestCase.assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 1);
  }

  public void testNewStyleUnbalanced() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{{{foo}}");
    TestCase.assertEquals(2, chunks.size());
  }

  public void testNewStyleEscapingAndValue() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{{{foo}}}");
    TestCase.assertEquals(3, chunks.size());
    assertEquals(TextRange.create(0, 2), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(2, 7), chunks.get(1).getTextRange());
    assertEquals(TextRange.create(7, 9), chunks.get(2).getTextRange());
  }

  public void testNewStyleSign() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:+}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertTrue(((NewStyleSubstitutionChunk)chunks.get(0)).hasSignOption());
  }

  public void testNewStyleAlternateForm() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:#}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertTrue(((NewStyleSubstitutionChunk)chunks.get(0)).useAlternateForm());
  }

  public void testNewStyleZeroPadded() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:0}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertTrue(((NewStyleSubstitutionChunk)chunks.get(0)).hasZeroPadding());
  }

  public void testNewStyleWidth() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:10}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 5), chunks.get(0).getTextRange());
    assertEquals("10", ((NewStyleSubstitutionChunk)chunks.get(0)).getWidth());
  }

  public void testNewStyleZeroPaddingWidth() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:010}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 6), chunks.get(0).getTextRange());
    assertTrue(((NewStyleSubstitutionChunk)chunks.get(0)).hasZeroPadding());
    assertEquals("10", ((NewStyleSubstitutionChunk)chunks.get(0)).getWidth());
  }

  public void testNewStyleThousandSeparator() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:,}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertTrue(((NewStyleSubstitutionChunk)chunks.get(0)).hasThousandsSeparator());
  }

  public void testNewStylePrecision() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:.2}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 5), chunks.get(0).getTextRange());
    assertEquals("2", ((NewStyleSubstitutionChunk)chunks.get(0)).getPrecision());
  }

  public void testNewStyleConversionType() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{:d}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertEquals('d', ((NewStyleSubstitutionChunk)chunks.get(0)).getConversionType());
  }

  public void testNewStyleConversion() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{!s}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 4), chunks.get(0).getTextRange());
    assertEquals("s", ((NewStyleSubstitutionChunk)chunks.get(0)).getConversion());
  }

  public void testNewStyleUnicodeEscaping() {
    List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("u\"\\N{LATIN SMALL LETTER B}{:s}\\N{NUMBER SIGN}\\\n" +
                                                                             "        {:s}\\N{LATIN SMALL LETTER B}\""));
    TestCase.assertEquals(2, chunks.size());
    assertEquals('s', chunks.get(0).getConversionType());
    assertEquals('s', chunks.get(1).getConversionType());
  }

  public void testNewStyleNestedFields() {
    final Field field = doParseAndGetFirstField("u'{foo:{bar} {baz}}'");
    TestCase.assertEquals("foo", field.getFirstName());
    final List<Field> nestedFields = field.getNestedFields();
    UsefulTestCase.assertSize(2, nestedFields);
    TestCase.assertEquals("bar", nestedFields.get(0).getFirstName());
    TestCase.assertEquals("baz", nestedFields.get(1).getFirstName());
  }

  public void testNewStyleTooNestedFields() {
    final ParseResult result = PyNewStyleStringFormatParser.parse("'{:{:{:{}}}} {}'");
    final List<Field> topLevelFields = result.getFields();
    UsefulTestCase.assertSize(2, topLevelFields);
    UsefulTestCase.assertSize(5, result.getAllFields());
    assertOrderedEquals(result.getAllFields().stream().map(Field::getDepth).toArray(), 1, 2, 3, 4, 1);
    assertOrderedEquals(result.getAllFields().stream().map(SubstitutionChunk::getAutoPosition).toArray(), 0, 1, 2, 3, 4);
  }

  public void testNewStyleAttrAndLookups() {
    Field field;

    field = doParseAndGetFirstField("u'{foo'");
    TestCase.assertEquals("foo", field.getFirstName());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());

    field = doParseAndGetFirstField("u'{foo}'");
    TestCase.assertEquals("foo", field.getFirstName());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());

    field = doParseAndGetFirstField("u'{foo.bar.baz}'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), ".bar", ".baz");

    field = doParseAndGetFirstField("u'{foo[bar][baz]}'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), "[bar]", "[baz]");

    field = doParseAndGetFirstField("u'{foo.bar[baz]}'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), ".bar", "[baz]");

    field = doParseAndGetFirstField("u'{foo.bar[baz}'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), ".bar");

    field = doParseAndGetFirstField("u'{foo[{bar[baz]}'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), "[{bar[baz]");

    field = doParseAndGetFirstField("u'{foo[{} {0} {bar.baz}]'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), "[{} {0} {bar.baz}]");

    field = doParseAndGetFirstField("u'{foo[bar]baz'");
    TestCase.assertEquals("foo", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), "[bar]");

    field = doParseAndGetFirstField("'{0[foo][.!:][}]}'");
    TestCase.assertEquals("0", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", "[.!:]", "[}]");

    field = doParseAndGetFirstField("'{.foo.bar}'");
    UsefulTestCase.assertEmpty(field.getFirstName());
    TestCase.assertEquals(TextRange.create(2, 2), field.getFirstNameRange());
    assertOrderedEquals(field.getAttributesAndLookups(), ".foo", ".bar");

    field = doParseAndGetFirstField("'{}'");
    UsefulTestCase.assertEmpty(field.getFirstName());
    TestCase.assertEquals(TextRange.create(2, 2), field.getFirstNameRange());

    field = doParseAndGetFirstField("'{:foo}'");
    UsefulTestCase.assertEmpty(field.getFirstName());
    TestCase.assertEquals(TextRange.create(2, 2), field.getFirstNameRange());

    field = doParseAndGetFirstField("'{!r:foo}'");
    UsefulTestCase.assertEmpty(field.getFirstName());
    TestCase.assertEquals(TextRange.create(2, 2), field.getFirstNameRange());
  }

  public void testNewStyleIncompleteAttrAndLookups() {

    Field field;

    field = doParseAndGetFirstField("'{foo}'");
    TestCase.assertEquals("foo", field.getFirstName());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());

    // recover the first name
    field = doParseAndGetFirstField("'{foo'");
    TestCase.assertEquals("foo", field.getFirstName());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());

    field = doParseAndGetFirstField("'{foo");
    TestCase.assertEquals("foo", field.getFirstName());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());

    field = doParseAndGetFirstField("'{0[foo].bar[baz]}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    // do not recover unfinished lookups
    field = doParseAndGetFirstField("'{0[foo].bar[ba}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar[ba!}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar[ba:}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar[ba'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar[ba");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    // do not recover illegal attributes
    field = doParseAndGetFirstField("'{0[foo].bar[baz]quux}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]quux!}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]quux:}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]quux'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0[foo].bar[baz]quux");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar", "[baz]");

    field = doParseAndGetFirstField("'{0..}'");
    TestCase.assertEquals("0", field.getFirstName());
    assertOrderedEquals(field.getAttributesAndLookups(), ".", ".");

    // recover attributes
    field = doParseAndGetFirstField("'{0[foo].}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".");

    field = doParseAndGetFirstField("'{0[foo].'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".");

    field = doParseAndGetFirstField("'{0[foo].");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".");

    field = doParseAndGetFirstField("'{0[foo].bar}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");

    field = doParseAndGetFirstField("'{0[foo].bar");
    assertOrderedEquals(field.getAttributesAndLookups(), "[foo]", ".bar");
  }

  public void testNewStyleAutoPosition() {
    final ParseResult result = PyNewStyleStringFormatParser.parse("'{foo} {} {bar} {0} {}'");
    final List<Field> topLevelFields = result.getFields();
    UsefulTestCase.assertSize(5, topLevelFields);

    assertEquals("foo", topLevelFields.get(0).getMappingKey());
    assertNull(topLevelFields.get(0).getManualPosition());
    assertNull(topLevelFields.get(0).getAutoPosition());

    assertNull(topLevelFields.get(1).getMappingKey());
    assertNull(topLevelFields.get(1).getManualPosition());
    TestCase.assertEquals(0, (int)topLevelFields.get(1).getAutoPosition());

    assertEquals("bar", topLevelFields.get(2).getMappingKey());
    assertNull(topLevelFields.get(2).getManualPosition());
    assertNull(topLevelFields.get(2).getAutoPosition());

    assertNull(topLevelFields.get(3).getMappingKey());
    TestCase.assertEquals(0, (int)topLevelFields.get(3).getManualPosition());
    assertNull(topLevelFields.get(3).getAutoPosition());

    assertNull(topLevelFields.get(4).getMappingKey());
    assertNull(topLevelFields.get(4).getManualPosition());
    TestCase.assertEquals(1, (int)topLevelFields.get(4).getAutoPosition());
  }

  public void testNewStyleNamedUnicodeEscapes() {
    final List<Field> fields = doParseAndGetTopLevelFields("u\"\\N{LATIN SMALL LETTER B}{:s}\\N{NUMBER SIGN}\\\n" +
                                                           "        {:s}\\N{LATIN SMALL LETTER B}\"");
    UsefulTestCase.assertSize(2, fields);
    TestCase.assertEquals(":s", fields.get(0).getFormatSpec());
    TestCase.assertEquals(":s", fields.get(1).getFormatSpec());
  }

  public void testNewStyleNamedUnicodeEscapeInLookup() {
    final Field field = doParseAndGetFirstField("'{foo[\\N{ESCAPE WITH ]}]}'");
    assertOrderedEquals(field.getAttributesAndLookups(), "[\\N{ESCAPE WITH ]}]");
  }

  public void testNewStyleNamedUnicodeEscapeInAttribute() {
    final Field field = doParseAndGetFirstField("'{foo.b\\N{ESCAPE WITH [}.b\\N{ESCAPE WITH .}}'");
    assertOrderedEquals(field.getAttributesAndLookups(), ".b\\N{ESCAPE WITH [}", ".b\\N{ESCAPE WITH .}");
  }

  public void testNewStyleUnclosedLookupEndsWithRightBrace() {
    final Field field = doParseAndGetFirstField("u'{0[}'");
    TestCase.assertEquals(-1, field.getRightBraceOffset());
    TestCase.assertEquals(6, field.getFieldEnd());
    UsefulTestCase.assertEmpty(field.getAttributesAndLookups());
  }

  @NotNull
  private static List<Field> doParseAndGetTopLevelFields(@NotNull String nodeText) {
    final ParseResult result = PyNewStyleStringFormatParser.parse(nodeText);
    return result.getFields();
  }

  @NotNull
  private static Field doParseAndGetFirstField(@NotNull String nodeText) {
    final ParseResult result = PyNewStyleStringFormatParser.parse(nodeText);
    final List<Field> topLevelFields = result.getFields();
    UsefulTestCase.assertSize(1, topLevelFields);
    return topLevelFields.get(0);
  }

  public void testNewStyleMappingKeyFormatSpec() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{a:d}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 5), chunks.get(0).getTextRange());
    assertEquals("a", ((NewStyleSubstitutionChunk)chunks.get(0)).getMappingKey());
    assertEquals('d', ((NewStyleSubstitutionChunk)chunks.get(0)).getConversionType());
  }

  public void testNewStyleMappingKeyConversionFormatSpec() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{a!s:.2d}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 9), chunks.get(0).getTextRange());
    assertEquals("a", ((NewStyleSubstitutionChunk)chunks.get(0)).getMappingKey());
    assertEquals("s", ((NewStyleSubstitutionChunk)chunks.get(0)).getConversion());
    assertEquals("2", ((NewStyleSubstitutionChunk)chunks.get(0)).getPrecision());
    assertEquals('d', ((NewStyleSubstitutionChunk)chunks.get(0)).getConversionType());
  }

  public void testSkipAlign() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{a:*^d}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 7), chunks.get(0).getTextRange());
    assertEquals("a", ((NewStyleSubstitutionChunk)chunks.get(0)).getMappingKey());
    assertEquals('d', ((NewStyleSubstitutionChunk)chunks.get(0)).getConversionType());
  }

  public void testNewStyleAllPossibleSpecs() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{a!s:#010,.2d}");
    TestCase.assertEquals(1, chunks.size());
    assertEquals(TextRange.create(0, 14), chunks.get(0).getTextRange());
    assertEquals("a", ((NewStyleSubstitutionChunk)chunks.get(0)).getMappingKey());
    assertEquals("s", ((NewStyleSubstitutionChunk)chunks.get(0)).getConversion());
    assertEquals("2", ((NewStyleSubstitutionChunk)chunks.get(0)).getPrecision());
    assertEquals('d', ((NewStyleSubstitutionChunk)chunks.get(0)).getConversionType());
  }
  
  public void testNewStyleFiledNameWithAttribute() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{foo.a}");
    TestCase.assertEquals(1, chunks.size());
    final NewStyleSubstitutionChunk chunk = (NewStyleSubstitutionChunk)chunks.get(0);
    assertEquals(TextRange.create(0, 7), chunk.getTextRange());
    assertEquals("foo", chunk.getMappingKey());
    assertNotNull(chunk.getMappingKeyAttributeName());
    assertEquals("a", chunk.getMappingKeyAttributeName());
  }
  
  public void testNewStyleFiledNameWithElementIndex() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{foo[a]}");
    TestCase.assertEquals(1, chunks.size());
    final NewStyleSubstitutionChunk chunk = (NewStyleSubstitutionChunk)chunks.get(0);
    assertEquals(TextRange.create(0, 8), chunk.getTextRange());
    assertEquals("foo", chunk.getMappingKey());
    assertNotNull(chunk.getMappingKeyElementIndex());
    assertEquals("a", chunk.getMappingKeyElementIndex());
  }

  public void testNewStyleFiledNameWithAttributeWithFormatSpec() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{foo.a:d}");
    TestCase.assertEquals(1, chunks.size());
    final NewStyleSubstitutionChunk chunk = (NewStyleSubstitutionChunk)chunks.get(0);
    assertEquals(TextRange.create(0, 9), chunk.getTextRange());
    assertEquals("foo", chunk.getMappingKey());
    assertNotNull(chunk.getMappingKeyAttributeName());
    assertEquals("a", chunk.getMappingKeyAttributeName());
    assertEquals('d', chunk.getConversionType());
  }

  public void testNewStyleFiledNameWithElementIndexWithFormatSpec() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{foo[a]:d}");
    TestCase.assertEquals(1, chunks.size());
    final NewStyleSubstitutionChunk chunk = (NewStyleSubstitutionChunk)chunks.get(0);
    assertEquals(TextRange.create(0, 10), chunk.getTextRange());
    assertEquals("foo", chunk.getMappingKey());
    assertNotNull(chunk.getMappingKeyElementIndex());
    assertEquals("a", chunk.getMappingKeyElementIndex());
    assertEquals('d', chunk.getConversionType());
  } 
  
  public void testNewStyleFieldNameWithNestedElementIndex() {
    final List<FormatStringChunk> chunks = parseNewStyleFormat("{foo[a][1].a[1]:d}");
    TestCase.assertEquals(1, chunks.size());
    final NewStyleSubstitutionChunk chunk = (NewStyleSubstitutionChunk)chunks.get(0);
    assertEquals(TextRange.create(0, 18), chunk.getTextRange());
    assertEquals("foo", chunk.getMappingKey());
    assertNotNull(chunk.getMappingKeyElementIndex());
    assertEquals("a", chunk.getMappingKeyElementIndex());
    assertEquals('d', chunk.getConversionType());
  }
  
  public void testAsciiFormatSpecifierOldStyleFormat() {
    final List<FormatStringChunk> chunks = parsePercentFormat("%a");
    TestCase.assertEquals(1, chunks.size());
    final PercentSubstitutionChunk chunk = (PercentSubstitutionChunk)chunks.get(0);
    assertEquals('a', chunk.getConversionType());
  }
}
