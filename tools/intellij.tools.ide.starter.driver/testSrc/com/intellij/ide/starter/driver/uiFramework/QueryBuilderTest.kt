package com.intellij.ide.starter.driver.uiFramework

import com.intellij.driver.sdk.ui.xQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.JPopupMenu

class QueryBuilderTest {

  @Test
  fun `test xQuery with single condition`() {
    val expected = "//div[@accessiblename='Stop']"
    val actual = xQuery { byAccessibleName("Stop") }

    assertEquals(expected, actual)
  }

  @Test
  fun `test xQuery with multiple condition with and`() {
    val expected = "//div[(@accessiblename='Stop' and @visible_text='2')]"
    val actual = xQuery { and(byAccessibleName("Stop"), byVisibleText("2")) }

    assertEquals(expected, actual)
  }

  @Test
  fun `test xQuery with multiple condition with or`() {
    val expected = "//div[(@visible_text='2' or @tooltiptext='Foo')]"
    val actual = xQuery { or(byVisibleText("2"), byTooltip("Foo")) }

    assertEquals(expected, actual)
  }

  @Test
  fun `test xQuery with nested conditions`() {
    val expected = "//div[(contains(@visible_text, '2') and @tooltiptext='Foo')]"
    val actual = xQuery { and(contains(byVisibleText("2")), byTooltip("Foo")) }

    assertEquals(expected, actual)
  }

  @Test
  fun `test xQuery with complex query`() {
    val expected = "//div[(@javaclass='javax.swing.Popup\$HeavyWeightWindow' or contains(@classhierarchy, 'javax.swing.Popup\$HeavyWeightWindow ') or contains(@classhierarchy, ' javax.swing.Popup\$HeavyWeightWindow '))][.//div[@class='NewNavBarPanel']]"
    val actual = xQuery { componentWithChild(byType("javax.swing.Popup${"$"}HeavyWeightWindow"), byClass("NewNavBarPanel")) }

    assertEquals(expected, actual)
  }

  @Test
  fun `test xQuery with complex query2`() {
    val expected = "//div[(@javaclass='javax.swing.JPopupMenu' or contains(@classhierarchy, 'javax.swing.JPopupMenu ') or contains(@classhierarchy, ' javax.swing.JPopupMenu '))][.//div[@accessiblename='Build']]"
    val actual = xQuery { componentWithChild(byType(JPopupMenu::class.java), byAccessibleName("Build")) }

    assertEquals(expected, actual)
  }
}