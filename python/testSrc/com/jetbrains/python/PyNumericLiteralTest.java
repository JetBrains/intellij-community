/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

public class PyNumericLiteralTest extends PyTestCase {

  public void testHexIntegers() {
    final int expectedInt = 38177486;
    final BigInteger expectedBigInt = new BigInteger("9223372037179284447");

    doTestIntegerLiteral("0x02468AcE", expectedInt);
    doTestIntegerLiteral("0x02468AcEl", expectedInt);
    doTestIntegerLiteral("0x02468AcEL", expectedInt);

    doTestIntegerLiteral("0x024_68_A_cE", expectedInt);
    doTestIntegerLiteral("0x024_68_A_cEl", expectedInt);
    doTestIntegerLiteral("0x024_68_A_cEL", expectedInt);

    doTestMoreThanLongIntegerLiteral("0x8000000013579BdF", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0x8000000013579BdFl", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0x8000000013579BdFL", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0x_8_00_000_0013_579BdF", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0x_8_00_000_0013_579BdFl", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0x_8_00_000_0013_579BdFL", expectedBigInt);


    doTestIntegerLiteral("0X02468AcE", expectedInt);
    doTestIntegerLiteral("0X02468AcEl", expectedInt);
    doTestIntegerLiteral("0X02468AcEL", expectedInt);

    doTestIntegerLiteral("0X024_68_A_cE", expectedInt);
    doTestIntegerLiteral("0X024_68_A_cEl", expectedInt);
    doTestIntegerLiteral("0X024_68_A_cEL", expectedInt);

    doTestMoreThanLongIntegerLiteral("0X8000000013579BdF", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0X8000000013579BdFl", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0X8000000013579BdFL", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0X_8_00_000_0013_579BdF", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0X_8_00_000_0013_579BdFl", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0X_8_00_000_0013_579BdFL", expectedBigInt);
  }

  public void testOctIntegers() {
    final int expectedInt = 16434824;
    final BigInteger expectedBigInt = new BigInteger("9223372036871210632");

    doTestIntegerLiteral("0o76543210", expectedInt);
    doTestIntegerLiteral("0o76543210l", expectedInt);
    doTestIntegerLiteral("0o76543210L", expectedInt);

    doTestIntegerLiteral("0o765_43_2_10", expectedInt);
    doTestIntegerLiteral("0o765_43_2_10l", expectedInt);
    doTestIntegerLiteral("0o765_43_2_10L", expectedInt);

    doTestMoreThanLongIntegerLiteral("0o1000000000000076543210", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0o1000000000000076543210l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0o1000000000000076543210L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0o_10_000_0000_00000_765432_10", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0o_10_000_0000_00000_765432_10l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0o_10_000_0000_00000_765432_10L", expectedBigInt);


    doTestIntegerLiteral("0O76543210", expectedInt);
    doTestIntegerLiteral("0O76543210l", expectedInt);
    doTestIntegerLiteral("0O76543210L", expectedInt);

    doTestIntegerLiteral("0O765_43_2_10", expectedInt);
    doTestIntegerLiteral("0O765_43_2_10l", expectedInt);
    doTestIntegerLiteral("0O765_43_2_10L", expectedInt);

    doTestMoreThanLongIntegerLiteral("0O1000000000000076543210", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0O1000000000000076543210l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0O1000000000000076543210L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0O_10_000_0000_00000_765432_10", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0O_10_000_0000_00000_765432_10l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0O_10_000_0000_00000_765432_10L", expectedBigInt);


    doTestIntegerLiteral("076543210", expectedInt);
    doTestIntegerLiteral("076543210l", expectedInt);
    doTestIntegerLiteral("076543210L", expectedInt);

    doTestIntegerLiteral("0765_43_2_10", expectedInt);
    doTestIntegerLiteral("0765_43_2_10l", expectedInt);
    doTestIntegerLiteral("0765_43_2_10L", expectedInt);

    doTestMoreThanLongIntegerLiteral("01000000000000076543210", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("01000000000000076543210l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("01000000000000076543210L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0_10_000_0000_00000_765432_10", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0_10_000_0000_00000_765432_10l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0_10_000_0000_00000_765432_10L", expectedBigInt);
  }

  public void testBinIntegers() {
    final int expectedInt = 240;
    final BigInteger expectedBigInt = new BigInteger("9223372036854775808");

    doTestIntegerLiteral("0b11110000", expectedInt);
    doTestIntegerLiteral("0b11110000l", expectedInt);
    doTestIntegerLiteral("0b11110000L", expectedInt);

    doTestIntegerLiteral("0b111_10_0_00", expectedInt);
    doTestIntegerLiteral("0b111_10_0_00l", expectedInt);
    doTestIntegerLiteral("0b111_10_0_00L", expectedInt);

    doTestMoreThanLongIntegerLiteral("0b1000000000000000000000000000000000000000000000000000000000000000", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0b1000000000000000000000000000000000000000000000000000000000000000l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0b1000000000000000000000000000000000000000000000000000000000000000L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0b_1_00_000_0000_000000000000000000000000000000000000000000000000000000", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0b_1_00_000_0000_000000000000000000000000000000000000000000000000000000l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0b_1_00_000_0000_000000000000000000000000000000000000000000000000000000L", expectedBigInt);


    doTestIntegerLiteral("0B11110000", expectedInt);
    doTestIntegerLiteral("0B11110000l", expectedInt);
    doTestIntegerLiteral("0B11110000L", expectedInt);

    doTestIntegerLiteral("0B111_10_0_00", expectedInt);
    doTestIntegerLiteral("0B111_10_0_00l", expectedInt);
    doTestIntegerLiteral("0B111_10_0_00L", expectedInt);

    doTestMoreThanLongIntegerLiteral("0B1000000000000000000000000000000000000000000000000000000000000000", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0B1000000000000000000000000000000000000000000000000000000000000000l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0B1000000000000000000000000000000000000000000000000000000000000000L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("0B_1_00_000_0000_000000000000000000000000000000000000000000000000000000", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0B_1_00_000_0000_000000000000000000000000000000000000000000000000000000l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("0B_1_00_000_0000_000000000000000000000000000000000000000000000000000000L", expectedBigInt);
  }

  public void testDecimalIntegers() {
    final int expectedInt = 123456789;
    final BigInteger expectedBigInt = new BigInteger("9223372036854775808");

    doTestIntegerLiteral("123456789", expectedInt);
    doTestIntegerLiteral("123456789l", expectedInt);
    doTestIntegerLiteral("123456789L", expectedInt);

    doTestIntegerLiteral("1_23_456_789", expectedInt);
    doTestIntegerLiteral("1_23_456_789l", expectedInt);
    doTestIntegerLiteral("1_23_456_789L", expectedInt);

    doTestMoreThanLongIntegerLiteral("9223372036854775808", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("9223372036854775808l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("9223372036854775808L", expectedBigInt);

    doTestMoreThanLongIntegerLiteral("9_22_337_2036_85477_5808", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("9_22_337_2036_85477_5808l", expectedBigInt);
    doTestMoreThanLongIntegerLiteral("9_22_337_2036_85477_5808L", expectedBigInt);
  }

  public void testZero() {
    doTestIntegerLiteral("0", 0);
    doTestIntegerLiteral("0l", 0);
    doTestIntegerLiteral("0L", 0);
  }

  public void testPointFloatNumbers() {
    final int expectedInt = 10;
    final BigInteger expectedBigInt = new BigInteger("9223372036854775808");

    doTestFloatLiteral("10.", expectedInt, new BigDecimal("10."));
    doTestFloatLiteral("1_0.", expectedInt, new BigDecimal("10."));

    doTestMoreThanLongFloatLiteral("9223372036854775808.", expectedBigInt, new BigDecimal("9223372036854775808."));
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_5808.", expectedBigInt, new BigDecimal("9223372036854775808."));


    doTestFloatLiteral(".10", 0, new BigDecimal(".10"));
    doTestFloatLiteral(".1_0", 0, new BigDecimal(".10"));


    doTestFloatLiteral("10.10", expectedInt, new BigDecimal("10.10"));
    doTestFloatLiteral("1_0.1_0", expectedInt, new BigDecimal("10.10"));

    doTestMoreThanLongFloatLiteral("9223372036854775808.10", expectedBigInt, new BigDecimal("9223372036854775808.10"));
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_5808.1_0", expectedBigInt, new BigDecimal("9223372036854775808.10"));
  }

  public void testExponentFloatNumbers() {
    final int expectedInt1 = 1000;
    final BigDecimal expectedFloatDecimal1 = new BigDecimal("1000.23");
    final BigDecimal expectedIntDecimal1 = new BigDecimal(expectedInt1);

    final int expectedInt2 = 1000230;
    final BigDecimal expectedIntDecimal2 = new BigDecimal(expectedInt2);

    final BigInteger expectedInt3 = new BigInteger("9223372036854775900");
    final BigDecimal expectedFloatDecimal3 = new BigDecimal("9223372036854775900.23");
    final BigDecimal expectedIntDecimal3 = new BigDecimal("9223372036854775900");

    doTestFloatLiteral("10.0023e2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10.0023E2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10.e2", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("10.E2", expectedInt1, expectedIntDecimal1);

    doTestFloatLiteral("1_0.00_23e0_2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("1_0.00_23E0_2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("1_0.e0_2", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("1_0.E0_2", expectedInt1, expectedIntDecimal1);

    doTestMoreThanLongFloatLiteral("92233720368547759.0023e2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.0023E2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.e2", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.E2", expectedInt3, expectedIntDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.0023e0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.0023E0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.e0_2", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.E0_2", expectedInt3, expectedIntDecimal3);


    doTestFloatLiteral("10.0023e+2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10.0023E+2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10.e+2", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("10.E+2", expectedInt1, expectedIntDecimal1);
    
    doTestFloatLiteral("1_0.00_23e+0_2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("1_0.00_23E+0_2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("1_0.e+0_2", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("1_0.E+0_2", expectedInt1, expectedIntDecimal1);

    doTestMoreThanLongFloatLiteral("92233720368547759.0023e+2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.0023E+2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.e+2", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("92233720368547759.E+2", expectedInt3, expectedIntDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.0023e+0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.0023E+0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.e+0_2", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_59.E+0_2", expectedInt3, expectedIntDecimal3);


    doTestFloatLiteral("10002.3e-1", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10002.3E-1", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10000.e-1", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("10000.E-1", expectedInt1, expectedIntDecimal1);

    doTestFloatLiteral("10_002.3_0e-0_1", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10_002.3_0E-0_1", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10_000.e-0_1", expectedInt1, expectedIntDecimal1);
    doTestFloatLiteral("10_000.E-0_1", expectedInt1, expectedIntDecimal1);

    doTestMoreThanLongFloatLiteral("922337203685477590023.00e-2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590023.00E-2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590023.e-2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590023.E-2", expectedInt3, expectedFloatDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590023.00e-0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590023.00E-0_2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590000.e-0_2", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590000.E-0_2", expectedInt3, expectedIntDecimal3);


    doTestFloatLiteral("100023e1", expectedInt2, expectedIntDecimal2);
    doTestFloatLiteral("100023E1", expectedInt2, expectedIntDecimal2);

    doTestFloatLiteral("10_00_23e0_1", expectedInt2, expectedIntDecimal2);
    doTestFloatLiteral("10_00_23E0_1", expectedInt2, expectedIntDecimal2);

    doTestMoreThanLongFloatLiteral("922337203685477590e1", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590E1", expectedInt3, expectedIntDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590e1", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590E1", expectedInt3, expectedIntDecimal3);


    doTestFloatLiteral("100023e+1", expectedInt2, expectedIntDecimal2);
    doTestFloatLiteral("100023E+1", expectedInt2, expectedIntDecimal2);

    doTestFloatLiteral("10_00_23e+0_1", expectedInt2, expectedIntDecimal2);
    doTestFloatLiteral("10_00_23E+0_1", expectedInt2, expectedIntDecimal2);

    doTestMoreThanLongFloatLiteral("922337203685477590e+1", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590E+1", expectedInt3, expectedIntDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590e+0_1", expectedInt3, expectedIntDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590E+0_1", expectedInt3, expectedIntDecimal3);


    doTestFloatLiteral("100023e-2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("100023E-2", expectedInt1, expectedFloatDecimal1);

    doTestFloatLiteral("10_00_23e-0_2", expectedInt1, expectedFloatDecimal1);
    doTestFloatLiteral("10_00_23E-0_2", expectedInt1, expectedFloatDecimal1);

    doTestMoreThanLongFloatLiteral("922337203685477590023e-2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("922337203685477590023E-2", expectedInt3, expectedFloatDecimal3);

    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590023e-2", expectedInt3, expectedFloatDecimal3);
    doTestMoreThanLongFloatLiteral("9_22_337_2036_85477_590023E-2", expectedInt3, expectedFloatDecimal3);
  }

  public void testImaginaryNumbers() {
    final int expectedInt1 = 1000;
    final BigDecimal expectedFloatDecimal1 = new BigDecimal("1000.23");
    final BigDecimal expectedIntDecimal1 = new BigDecimal("1000");

    final BigInteger expectedInt2 = new BigInteger("9223372036854775808");

    doTestComplexLiteral("1000.2_3j", expectedInt1, expectedFloatDecimal1);
    doTestComplexLiteral("1000.2_3J", expectedInt1, expectedFloatDecimal1);

    doTestComplexLiteral("10_00.2_3j", expectedInt1, expectedFloatDecimal1);
    doTestComplexLiteral("10_00.2_3J", expectedInt1, expectedFloatDecimal1);

    doTestMoreThanLongComplexLiteral("9223372036854775808.10j", expectedInt2, new BigDecimal("9223372036854775808.10"));
    doTestMoreThanLongComplexLiteral("9223372036854775808.10J", expectedInt2, new BigDecimal("9223372036854775808.10"));

    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_5808.1_0j", expectedInt2, new BigDecimal("9223372036854775808.10"));
    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_5808.1_0J", expectedInt2, new BigDecimal("9223372036854775808.10"));


    doTestComplexLiteral("10.0023e0_2j", expectedInt1, expectedFloatDecimal1);
    doTestComplexLiteral("10.0023e0_2J", expectedInt1, expectedFloatDecimal1);

    doTestComplexLiteral("1_0.00_23e0_2j", expectedInt1, expectedFloatDecimal1);
    doTestComplexLiteral("1_0.00_23e0_2J", expectedInt1, expectedFloatDecimal1);

    doTestMoreThanLongComplexLiteral("922337203685477580.810e1j", expectedInt2, new BigDecimal("9223372036854775808.10"));
    doTestMoreThanLongComplexLiteral("922337203685477580.810e1J", expectedInt2, new BigDecimal("9223372036854775808.10"));

    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_580.810e0_1j", expectedInt2, new BigDecimal("9223372036854775808.10"));
    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_580.810e0_1J", expectedInt2, new BigDecimal("9223372036854775808.10"));


    doTestComplexLiteral("1000j", expectedInt1, expectedIntDecimal1);
    doTestComplexLiteral("1000J", expectedInt1, expectedIntDecimal1);

    doTestComplexLiteral("10_0_0j", expectedInt1, expectedIntDecimal1);
    doTestComplexLiteral("10_0_0J", expectedInt1, expectedIntDecimal1);

    doTestMoreThanLongComplexLiteral("9223372036854775808j", expectedInt2, new BigDecimal("9223372036854775808"));
    doTestMoreThanLongComplexLiteral("9223372036854775808J", expectedInt2, new BigDecimal("9223372036854775808"));

    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_5808j", expectedInt2, new BigDecimal("9223372036854775808"));
    doTestMoreThanLongComplexLiteral("9_22_337_2036_85477_5808J", expectedInt2, new BigDecimal("9223372036854775808"));
  }

  private void doTestIntegerLiteral(@NotNull String text, int expected) {
    doTestLiteral(text, true, Long.valueOf(expected), BigInteger.valueOf(expected), BigDecimal.valueOf(expected), "int");
  }

  private void doTestMoreThanLongIntegerLiteral(@NotNull String text, @NotNull BigInteger expected) {
    doTestLiteral(text, true, null, expected, new BigDecimal(expected), "int");
  }

  private void doTestFloatLiteral(@NotNull String text, int expectedInt, @NotNull BigDecimal expectedDecimal) {
    doTestLiteral(text, false, Long.valueOf(expectedInt), BigInteger.valueOf(expectedInt), expectedDecimal, "float");
  }

  private void doTestMoreThanLongFloatLiteral(@NotNull String text, @NotNull BigInteger expectedInt, @NotNull BigDecimal expectedDecimal) {
    doTestLiteral(text, false, null, expectedInt, expectedDecimal, "float");
  }

  private void doTestComplexLiteral(@NotNull String text, int expectedInt, @NotNull BigDecimal expectedDecimal) {
    doTestLiteral(text, false, Long.valueOf(expectedInt), BigInteger.valueOf(expectedInt), expectedDecimal, "complex");
  }

  private void doTestMoreThanLongComplexLiteral(@NotNull String text, @NotNull BigInteger expectedInt, @NotNull BigDecimal expectedDecimal) {
    doTestLiteral(text, false, null, expectedInt, expectedDecimal, "complex");
  }

  private void doTestLiteral(@NotNull String text,
                             boolean isInteger,
                             @Nullable Long expectedLong,
                             @NotNull BigInteger expectedInt,
                             @NotNull BigDecimal expectedDecimal,
                             @NotNull String expectedType) {
    final PyNumericLiteralExpression literal = configureByText(text);
    assertEquals(isInteger, literal.isIntegerLiteral());

    assertEquals(expectedLong, literal.getLongValue());
    assertEquals(expectedInt, literal.getBigIntegerValue());
    assertEquals(0, expectedDecimal.compareTo(literal.getBigDecimalValue()));

    final PyType type = TypeEvalContext.codeInsightFallback(myFixture.getProject()).getType(literal);
    assertNotNull(type);
    assertEquals(expectedType, type.getName());
  }

  @NotNull
  private PyNumericLiteralExpression configureByText(@NotNull String text) {
    final PsiElement element = myFixture.configureByText(PythonFileType.INSTANCE, text).findElementAt(0);
    assertNotNull(element);

    final PyNumericLiteralExpression numericLiteralExpression = PyUtil.as(element.getParent(), PyNumericLiteralExpression.class);
    assertNotNull(numericLiteralExpression);

    return numericLiteralExpression;
  }
}
