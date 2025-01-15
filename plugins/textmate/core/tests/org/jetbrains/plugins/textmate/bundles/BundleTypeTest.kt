package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.TestUtil.getBundleDirectory
import org.junit.Test
import kotlin.test.assertEquals

class BundleTypeTest {
  @Test
  fun testDefineTextMateType() {
    assertEquals(BundleType.TEXTMATE, BundleType.detectBundleType(getBundleDirectory(TestUtil.MARKDOWN_TEXTMATE)));
  }

  @Test
  fun testDefineSublimeType() {
    assertEquals(BundleType.SUBLIME, BundleType.detectBundleType(getBundleDirectory(TestUtil.MARKDOWN_SUBLIME)));
  }

  @Test
  fun testInvalidType() {
    assertEquals(BundleType.UNDEFINED, BundleType.detectBundleType(getBundleDirectory(TestUtil.INVALID_BUNDLE)));
  }

  @Test
  fun testDefineSublimeTypeWithInfoPlist() {
    assertEquals(BundleType.SUBLIME, BundleType.detectBundleType(getBundleDirectory(TestUtil.LARAVEL_BLADE)));
  }

  @Test
  fun testVSCode() {
    assertEquals(BundleType.VSCODE, BundleType.detectBundleType(getBundleDirectory(TestUtil.BAT)));
  }
}
