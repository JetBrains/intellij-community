package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderSingularTest extends AbstractLombokParsingTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaBiMap() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaCollection() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaList() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaMap() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaSet() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaSortedMap() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaSortedSet() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Guava$SingularGuavaTable() throws IOException {
    doTest(true);
  }


  public void testBuilder$Singular$Generic$Util$Collection$SingularCollection() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Collection$SingularIterable() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Collection$SingularList() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Collection$SingularNavigableSet() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Collection$SingularSet() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Collection$SingularSortedSet() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Map$SingularMap() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Map$SingularNavigableMap() throws IOException {
    doTest(true);
  }

  public void testBuilder$Singular$Generic$Util$Map$SingularSortedMap() throws IOException {
    doTest(true);
  }
}
