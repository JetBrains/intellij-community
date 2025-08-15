package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.TestUtilMultiplatform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BundleTypeTest {
  @Test
  fun testDefineTextMateType() {
    assertEquals(BundleType.TEXTMATE, BundleType.detectBundleType(TestUtilMultiplatform.getResourceReader(TestUtil.MARKDOWN_TEXTMATE), TestUtil.MARKDOWN_TEXTMATE))
  }

  @Test
  fun testDefineSublimeType() {
    assertEquals(BundleType.SUBLIME, BundleType.detectBundleType(TestUtilMultiplatform.getResourceReader(TestUtil.MARKDOWN_SUBLIME), TestUtil.MARKDOWN_SUBLIME))
  }

  @Test
  fun testInvalidType() {
    assertEquals(BundleType.UNDEFINED, BundleType.detectBundleType(TestUtilMultiplatform.getResourceReader(TestUtil.INVALID_BUNDLE), TestUtil.INVALID_BUNDLE))
  }

  @Test
  fun testDefineSublimeTypeWithInfoPlist() {
    assertEquals(BundleType.SUBLIME, BundleType.detectBundleType(TestUtilMultiplatform.getResourceReader(TestUtil.LARAVEL_BLADE), TestUtil.LARAVEL_BLADE))
  }

  @Test
  fun testVSCode() {
    assertEquals(BundleType.VSCODE, BundleType.detectBundleType(TestUtilMultiplatform.getResourceReader(TestUtil.BAT), TestUtil.BAT))
  }
}