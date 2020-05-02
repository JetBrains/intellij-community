package org.jetbrains.plugins.textmate.bundles;

import org.jetbrains.plugins.textmate.TestUtil;
import org.junit.Test;

import static org.jetbrains.plugins.textmate.TestUtil.getBundleDirectory;
import static org.junit.Assert.assertEquals;

public class BundleTypeTest {
  @Test
  public void testDefineTextMateType() {
    assertEquals(BundleType.TEXTMATE, BundleType.fromDirectory(getBundleDirectory(TestUtil.MARKDOWN_TEXTMATE)));
  }

  @Test
  public void testDefineSublimeType() {
    assertEquals(BundleType.SUBLIME, BundleType.fromDirectory(getBundleDirectory(TestUtil.MARKDOWN_SUBLIME)));
  }

  @Test
  public void testInvalidType() {
    assertEquals(BundleType.UNDEFINED, BundleType.fromDirectory(getBundleDirectory(TestUtil.INVALID_BUNDLE)));
  }

  @Test
  public void testDefineSublimeTypeWithInfoPlist() {
    assertEquals(BundleType.SUBLIME, BundleType.fromDirectory(getBundleDirectory(TestUtil.LARAVEL_BLADE)));
  }

  @Test
  public void testVSCode() {
    assertEquals(BundleType.VSCODE, BundleType.fromDirectory(getBundleDirectory(TestUtil.BAT)));
  }
}
