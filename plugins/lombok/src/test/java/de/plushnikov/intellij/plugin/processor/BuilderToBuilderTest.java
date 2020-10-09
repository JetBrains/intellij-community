package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderToBuilderTest extends AbstractLombokParsingTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testBuilder$BuilderWithToBuilder() {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnClass() {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnConstructor() {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnMethod() {
    doTest(true);
  }

}
