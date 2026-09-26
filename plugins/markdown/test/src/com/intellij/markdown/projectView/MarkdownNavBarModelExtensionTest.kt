// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.projectView

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

class MarkdownNavBarModelExtensionTest : BasePlatformTestCase() {
  fun `test header hierarchy uses structure view presentation`() {
    val file = myFixture.configureByText(
      "test.md",
      "# Parent\n### Grandchild\n\nSetext heading\n===\nText under setext heading\n\n# ![logo](a.png)\n\n#",
    )
    val headers = PsiTreeUtil.findChildrenOfType(file, MarkdownHeader::class.java).toList()
    assertEquals(5, headers.size)
    val (parent, grandchild, setext, imageHeader, emptyHeader) = headers
    val extension = MarkdownNavBarModelExtension()

    assertEquals("Parent", extension.getPresentableText(parent))
    assertEquals("Grandchild", extension.getPresentableText(grandchild))
    assertEquals("Setext heading", extension.getPresentableText(setext))
    assertEquals("logo", extension.getPresentableText(imageHeader))
    assertNull(extension.getPresentableText(emptyHeader))
    assertEquals("test.md", extension.getPresentableText(file))
    assertSame(parent, extension.getParent(grandchild))
    assertSame(file, extension.getParent(parent))
    assertSame(file, extension.getParent(setext))
  }

  fun `test caret in text under heading selects that heading`() {
    val file = myFixture.configureByText("test.md", "# Parent\n\n### Child\n\nText <caret>under child")
    val child = PsiTreeUtil.findChildrenOfType(file, MarkdownHeader::class.java).last()
    val settings = UISettings.getInstance()
    val oldShowMembers = settings.showMembersInNavigationBar
    val extension = MarkdownNavBarModelExtension()

    try {
      settings.showMembersInNavigationBar = true
      assertSame(child, extension.getLeafElement(dataMap(file, myFixture.editor)))

      settings.showMembersInNavigationBar = false
      assertNull(extension.getLeafElement(dataMap(file, myFixture.editor)))
    }
    finally {
      settings.showMembersInNavigationBar = oldShowMembers
    }
  }

  fun `test caret inside html block selects containing heading`() {
    val file = myFixture.configureByText("test.md", "# Heading\n\n<table><tr><td>ce<caret>ll</td></tr></table>")
    val heading = PsiTreeUtil.findChildOfType(file, MarkdownHeader::class.java)!!
    val settings = UISettings.getInstance()
    val oldShowMembers = settings.showMembersInNavigationBar
    val extension = MarkdownNavBarModelExtension()

    try {
      settings.showMembersInNavigationBar = true
      assertSame(heading, extension.getLeafElement(dataMap(file, myFixture.editor)))
      val elementAtCaret = file.findElementAt(myFixture.editor.caretModel.offset)!!
      assertNull(extension.getPresentableText(elementAtCaret))
    }
    finally {
      settings.showMembersInNavigationBar = oldShowMembers
    }
  }

  fun `test list nodes use their structure view presentation`() {
    val file = myFixture.configureByText("test.md", "# Heading\n\n- First item")
    val header = PsiTreeUtil.findChildOfType(file, MarkdownHeader::class.java)!!
    val list = PsiTreeUtil.findChildOfType(file, MarkdownList::class.java)!!
    val listItem = PsiTreeUtil.findChildOfType(file, MarkdownListItem::class.java)!!
    val registryValue = Registry.get("markdown.structure.view.list.visibility")
    val oldListVisibility = Registry.`is`("markdown.structure.view.list.visibility")
    val extension = MarkdownNavBarModelExtension()

    try {
      registryValue.setValue(true)
      assertEquals("Unordered list", extension.getPresentableText(list))
      assertEquals("-", extension.getPresentableText(listItem))
      assertSame(header, extension.getParent(list))
      assertSame(list, extension.getParent(listItem))
    }
    finally {
      registryValue.setValue(oldListVisibility)
    }
  }

  private fun dataMap(file: PsiFile, editor: Editor): DataMap {
    val values = mapOf<DataKey<*>, Any>(CommonDataKeys.PSI_FILE to file, CommonDataKeys.EDITOR to editor)
    return object : DataMap {
      @Suppress("UNCHECKED_CAST")
      override fun <T : Any> get(key: DataKey<T>): T? = values[key] as? T
    }
  }
}
