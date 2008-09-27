package org.jetbrains.plugins.ruby.testing.sm;

import com.intellij.testFramework.UsefulTestCase;

/**
 * @author Roman Chernyatchik
 */
public class LocationProviderUtilTest extends UsefulTestCase {
  public void testExtractProtocol() {
    assertEquals(null,
                 LocationProviderUtil.extractProtocol(""));
    assertEquals(null,
                 LocationProviderUtil.extractProtocol("file:/"));

    assertEquals("file",
                 LocationProviderUtil.extractProtocol("file://"));
    assertEquals("file",
                 LocationProviderUtil.extractProtocol("file:///some/path/file.rb:24"));
    assertEquals("file",
                 LocationProviderUtil.extractProtocol("file://./some/path/file.rb:24"));

    assertEquals("ruby_qn",
                 LocationProviderUtil.extractProtocol("ruby_qn://"));
    assertEquals("ruby_qn",
                 LocationProviderUtil.extractProtocol("ruby_qn://A::B.method"));
  }

  public void testExtractPath() {
    assertEquals(null,
                 LocationProviderUtil.extractPath(""));
    assertEquals(null,
                 LocationProviderUtil.extractPath("file:/"));

    assertEquals("",
                 LocationProviderUtil.extractPath("file://"));
    assertEquals("/some/path/file.rb:24",
                 LocationProviderUtil.extractPath("file:///some/path/file.rb:24"));
    assertEquals("./some/path/file.rb:24",
                 LocationProviderUtil.extractPath("file://./some/path/file.rb:24"));

    assertEquals("",
                 LocationProviderUtil.extractPath("ruby_qn://"));
    assertEquals("A::B.method", 
                 LocationProviderUtil.extractPath("ruby_qn://A::B.method"));
  }
}
