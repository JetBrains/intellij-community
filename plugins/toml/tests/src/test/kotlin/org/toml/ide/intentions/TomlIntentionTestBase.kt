/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import kotlin.reflect.KClass

abstract class TomlIntentionTestBase(private val intentionClass: KClass<out IntentionAction>): TomlTestBase() {

    protected open val previewExpected: Boolean get() = findIntention()?.startInWriteAction() == true

    private fun findIntention(): IntentionAction? = myFixture.availableIntentions.firstOrNull {
        val originalIntention = IntentionActionDelegate.unwrap(it)
        intentionClass == originalIntention::class
    }

    protected fun doAvailableTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        filename: String = "example.toml"
    ) {
        InlineFile(before.trimIndent(), filename)
        launchAction()
        myFixture.checkResult(after.trimIndent())
    }

    protected fun doUnavailableTest(
        @Language("TOML") before: String,
        filename: String = "example.toml"
    ) {
        InlineFile(before.trimIndent(), filename)

        val intention = findIntention()
        check(intention == null) {
            "\"${intentionClass.simpleName}\" should not be available"
        }
    }

    private fun launchAction() {
        UIUtil.dispatchAllInvocationEvents()

        val intention = findIntention() ?: error("Failed to find ${intentionClass.simpleName} intention")
        val tomlIntention = intention is TomlElementBaseIntentionAction<*>
        if (tomlIntention) {
            if (previewExpected) {
                myFixture.checkPreviewAndLaunchAction(intention)
            } else {
                val previewInfo = IntentionPreviewPopupUpdateProcessor.getPreviewInfo(project, intention, myFixture.file, myFixture.editor)
                assertEquals(IntentionPreviewInfo.EMPTY, previewInfo)
                myFixture.launchAction(intention)
            }
        } else {
            myFixture.launchAction(intention)
        }
    }

    protected fun checkAvailableInSelectionOnly(@Language("TOML") code: String, filename: String = "example.toml") {
        InlineFile(code.replace("<selection>", "<selection><caret>"), filename)

        val selections = myFixture.editor.selectionModel.let { model ->
            model.blockSelectionStarts.zip(model.blockSelectionEnds)
                .map { TextRange(it.first, it.second + 1) }
        }

        val intention = findIntention() ?: error("Failed to find ${intentionClass.simpleName} intention")
        for (pos in myFixture.file.text.indices) {
            myFixture.editor.caretModel.moveToOffset(pos)

            val expectAvailable = selections.any { it.contains(pos) }
            val isAvailable = intention.isAvailable(project, myFixture.editor, myFixture.file)

            check(isAvailable == expectAvailable) {
                "Expect ${if (expectAvailable) "available" else "unavailable"}, " +
                    "actually ${if (isAvailable) "available" else "unavailable"} " +
                    "at `${StringBuilder(myFixture.file.text).insert(pos, "<caret>")}`"
            }
        }
    }
}
