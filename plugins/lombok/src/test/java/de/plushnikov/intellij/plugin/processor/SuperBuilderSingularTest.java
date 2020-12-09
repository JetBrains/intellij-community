package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class SuperBuilderSingularTest extends AbstractLombokParsingTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableSet<E> extends java.util.SortedSet<E> {}");
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaBiMap() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaCollection() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaList() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaMap() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSet() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSortedMap() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSortedSet() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaTable() {
    doTest(true);
  }


  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularCollection() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularIterable() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularList() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularNavigableSet() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularSet() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularSortedSet() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Map$SingularMap() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Map$SingularNavigableMap() {
    doTest(true);
  }

  public void testSuperbuilder$Singular$Generic$Util$Map$SingularSortedMap() {
    doTest(true);
  }
}
