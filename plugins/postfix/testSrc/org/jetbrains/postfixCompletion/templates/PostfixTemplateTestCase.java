package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

abstract public class PostfixTemplateTestCase extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package java.lang;\n" +
                       "public final class Boolean implements java.io.Serializable, Comparable<Boolean> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public final class Byte implements java.io.Serializable, Comparable<Byte> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public interface Iterable<T> {}");
    myFixture.addClass("package java.util;\n" +
                       "public class ArrayList<E> extends AbstractList<E>\n" +
                       "        implements List<E>, Iterable<E>, RandomAccess, Cloneable, java.io.Serializable {}");
  }
}
