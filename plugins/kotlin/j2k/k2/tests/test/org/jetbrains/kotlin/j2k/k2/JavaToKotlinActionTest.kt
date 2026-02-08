//  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class JavaToKotlinActionTest : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    private val actionManager: ActionManager
        get() = ActionManager.getInstance()

    fun testStandaloneActionVisibleWhenNoAiPlugin() {
        myFixture.configureByText(
            "Test.java", """
            public class Test {
                public void foo() {}
            }
        """.trimIndent()
        )

        val standaloneAction = actionManager.getAction("ConvertJavaToKotlin")
            ?: throw AssertionError("ConvertJavaToKotlin action should be registered")

        val actionGroup = actionManager.getAction("ConvertJavaToKotlinGroup") as? DefaultActionGroup
            ?: throw AssertionError("ConvertJavaToKotlinGroup action group should be registered")

        val standaloneEvent = createActionEvent()
        val groupEvent = createActionEvent()

        standaloneAction.update(standaloneEvent)
        actionGroup.update(groupEvent)

        assertTrue(
            "Standalone action should be visible when group has only 1 action",
            standaloneEvent.presentation.isEnabledAndVisible
        )

        assertFalse(
            "Action group should be hidden when it has only 1 child",
            groupEvent.presentation.isEnabledAndVisible
        )

        assertEquals(
            "Standalone action should have the default text",
            "Convert Java to Kotlin",
            standaloneAction.templatePresentation.text
        )
    }

    fun testActionGroupVisibleWhenAiPluginPresent() {
        myFixture.configureByText(
            "Test.java", """
            public class Test {
                public void foo() {}
            }
        """.trimIndent()
        )

        val standaloneAction = actionManager.getAction("ConvertJavaToKotlin")
            ?: throw AssertionError("ConvertJavaToKotlin action should be registered")

        val actionGroup = actionManager.getAction("ConvertJavaToKotlinGroup") as? DefaultActionGroup
            ?: throw AssertionError("ConvertJavaToKotlinGroup action group should be registered")

        val builtInActionInGroup = actionManager.getAction("ConvertJavaToKotlinInGroup")
            ?: throw AssertionError("ConvertJavaToKotlinInGroup action should be registered")

        val mockAiAction = object : AnAction("AI-assisted") {
            override fun actionPerformed(e: AnActionEvent) {}
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }
        }

        actionGroup.add(mockAiAction)

        try {
            val standaloneEvent = createActionEvent()
            val groupEvent = createActionEvent()

            standaloneAction.update(standaloneEvent)
            actionGroup.update(groupEvent)

            assertFalse(
                "Standalone action should be hidden when group has 2+ actions",
                standaloneEvent.presentation.isVisible
            )

            assertTrue(
                "Action group should be visible when it has 2+ children",
                groupEvent.presentation.isEnabledAndVisible
            )

            assertTrue(
                "Action group should be a popup",
                groupEvent.presentation.isPopupGroup
            )

            assertEquals(
                "Built-in action in group should have 'Built-in' text",
                "Built-in",
                builtInActionInGroup.templatePresentation.text
            )
        } finally {
            actionGroup.remove(mockAiAction)
        }
    }

    fun testStandaloneActionHiddenWithNonJavaFile() {
        myFixture.configureByText(
            "Test.kt", """
            class Test {
                fun foo() {}
            }
        """.trimIndent()
        )

        val standaloneAction = actionManager.getAction("ConvertJavaToKotlin")
            ?: throw AssertionError(
                "ConvertJavaToKotlin action should be registered. " +
                        "Available actions: ${actionManager.getActionIdList("").joinToString { it }}"
            )

        val event = createActionEvent()
        standaloneAction.update(event)

        assertFalse(
            "Standalone action should be disabled for non-Java files",
            event.presentation.isEnabledAndVisible
        )
    }

    private fun createActionEvent(): AnActionEvent {
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(myFixture.file.virtualFile))
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .build()

        return TestActionEvent.createTestEvent(dataContext)
    }
}