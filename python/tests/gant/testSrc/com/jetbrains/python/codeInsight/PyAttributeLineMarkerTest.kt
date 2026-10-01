// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The gutter marker of a class attribute that overrides an attribute of a base class. */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(issues = ["PY-92835"], classes = [PyLineMarkerProvider::class])
class PyAttributeLineMarkerTest : PyCodeInsightTestCase() {

  @Test
  fun `the provider gives the marker for the name of an overriding attribute, not for the target`() {
    val file = myFixture.configureByText("a.py", """
      class Base:
          size = 1

      class Child(Base):
          size = 2
    """.trimIndent()) as PyFile
    runReadActionBlocking {
      val target = file.findTopLevelClass("Child")!!.findClassAttribute("size", false, null)!!
      val provider = PyLineMarkerProvider()
      assertNull(provider.getLineMarkerInfo(target))
      assertNotNull(provider.getLineMarkerInfo(target.nameIdentifier!!))
    }
  }

  @Test
  fun `an overriding attribute in a doctest gets its marker on the name leaf`() {
    myFixture.configureByText("b.py", """
      def f():
          '''
          >>> class Base:
          ...     size = 1
          >>> class Child(Base):
          ...     size = 2
          '''
    """.trimIndent())
    myFixture.doHighlighting()
    runReadActionBlocking {
      val tooltip = PyCodeInsightBundle.message("line.markers.tooltip.overrides.attribute.in.class", "Base")
      val markers = LineMarkersPass.getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
        .filter { it.lineMarkerTooltip == tooltip }
      val element = markers.single().element!!
      assertNull(element.firstChild, "the marker element is not a leaf: $element")
      assertEquals("size", element.text)
      assertEquals("Child", (element.parent as PyTargetExpression).containingClass?.name)
    }
  }
}
