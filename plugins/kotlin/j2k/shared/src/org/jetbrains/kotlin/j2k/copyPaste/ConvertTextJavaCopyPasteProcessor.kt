// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferable
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.util.LocalTimeCounter
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(ConvertTextJavaCopyPasteProcessor::class.java)

/**
 * Handles copy-pasting of arbitrary text (not from a Java file inside the IDE) into a Kotlin file.
 * If the text looks like Java code, it will be converted by J2K.
 *
 * Since we don't have an original Java file from which to take the PSI context,
 * we prepare a dummy file with some constructed context and place the copy-pasted code in this file before conversion.
 *
 * See also [ConvertJavaCopyPasteProcessor] for the related case of code copy-pasted from a Java file into a Kotlin file.
 *
 * Tests: [org.jetbrains.kotlin.j2k.k2.K2TextJavaToKotlinCopyPasteConversionTestGenerated].
 */
class ConvertTextJavaCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    private class MyTransferableData(val text: String) : TextBlockTransferableData {
        override fun getFlavor() = DATA_FLAVOR

        companion object {
            val DATA_FLAVOR: DataFlavor =
                DataFlavor(ConvertTextJavaCopyPasteProcessor::class.java, "class: ConvertTextJavaCopyPasteProcessor")
        }
    }

    /**
     * The Kotlin context into which the Java code was pasted.
     * Needed to determine the [JavaContext].
     */
    private enum class KotlinContext { TOP_LEVEL, CLASS_BODY, IN_BLOCK, EXPRESSION }

    /**
     * The context in which the Java code needs to be converted by J2K.
     * For example, if we are converting a single expression, it will be wrapped
     * into a dummy variable declaration, a method, and a class, before J2K conversion.
     */
    private enum class JavaContext { TOP_LEVEL, CLASS_BODY, IN_BLOCK, EXPRESSION }

    /**
     * On "copy" action, remember if we copy from a Kotlin file. In this case, J2K should do nothing.
     */
    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<TextBlockTransferableData> {
        return if (file is KtFile) listOf(CopiedKotlinCode()) else emptyList()
    }

    override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> {
        try {
            // Code was copied from a Kotlin file, don't run J2K on it
            if (content.isDataFlavorSupported(CopiedKotlinCode.DATA_FLAVOR)) return emptyList()

            // Code was copied from a Java file, this is handled by ConvertJavaCopyPasteProcessor
            if (content.isDataFlavorSupported(CopiedJavaCode.DATA_FLAVOR)) return emptyList()

            if (!content.isDataFlavorSupported(DataFlavor.stringFlavor)) return emptyList()

            val text = content.getTransferData(DataFlavor.stringFlavor) as String
            return listOf(MyTransferableData(text))
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
        }

        return emptyList()
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<TextBlockTransferableData>
    ) {
        if (DumbService.getInstance(project).isDumb) return
        if (!KotlinEditorOptions.getInstance().isEnableJavaToKotlinConversion) return

        val transferableData = values.single() as MyTransferableData
        val text = TextBlockTransferable.convertLineSeparators(editor, transferableData.text, values)
        val targetData = getTargetData(project, editor.document, caretOffset, bounds) ?: return
        PsiDocumentManager.getInstance(project).commitDocument(targetData.document)

        val pasteTarget = detectPasteTarget(targetData.file, targetData.bounds.startOffset, targetData.bounds.endOffset) ?: return
        val javaConversionContext = detectJavaConversionContext(pasteTarget.kotlinPasteContext, text, project) ?: return

        if (!confirmConvertJavaOnPaste(project, isPlainText = true)) return

        val copiedJavaCode = prepareCopiedJavaCodeByContext(text, javaConversionContext, pasteTarget)
        val conversionData = ConversionData.prepare(copiedJavaCode, project)
        val j2kKind = getJ2kKind(targetData.file)

        val converter = J2KTextCopyPasteConverter(project, editor, conversionData, targetData, j2kKind)
        val conversionTime = measureTimeMillis { converter.convert() }
        J2KFusCollector.log(
            type = ConversionType.TEXT_EXPRESSION,
            isNewJ2k = j2kKind == K1_NEW,
            conversionTime,
            linesCount = conversionData.elementsAndTexts.lineCount(),
            filesCount = 1
        )

        Util.conversionPerformed = true
    }

    private val KtElement.kotlinPasteContext: KotlinContext
        get() = when (this) {
            is KtFile -> KotlinContext.TOP_LEVEL
            is KtClassBody -> KotlinContext.CLASS_BODY
            is KtBlockExpression -> KotlinContext.IN_BLOCK
            else -> KotlinContext.EXPRESSION
        }

    /**
     * Returns the closest `KtElement` into which we are pasting the Java code.
     */
    private fun detectPasteTarget(file: KtFile, startOffset: Int, endOffset: Int): KtElement? {
        if (!isConversionSupportedAtPosition(file, startOffset)) return null

        val fileText = file.text
        val dummyDeclarationText = "fun dummy(){}"
        val newFileText = "${fileText.substring(0, startOffset)} $dummyDeclarationText\n${fileText.substring(endOffset)}"

        val newFile = parseAsFile(newFileText, KotlinFileType.INSTANCE, file.project)
        (newFile as KtFile).analysisContext = file

        val funKeyword = newFile.findElementAt(startOffset + 1) ?: return null
        if (funKeyword.node.elementType != KtTokens.FUN_KEYWORD) return null
        val declaration = funKeyword.parent as? KtFunction ?: return null

        return declaration.parent as? KtElement
    }

    private fun detectJavaConversionContext(pasteContext: KotlinContext, text: String, project: Project): JavaContext? {
        fun JavaContext.check(): JavaContext? = this.takeIf { isParsedAsJavaCode(text, it, project) }

        if (isParsedAsKotlinCode(text, pasteContext, project)) return null

        return when (pasteContext) {
            KotlinContext.TOP_LEVEL -> {
                JavaContext.TOP_LEVEL.check()?.let { return it }
                JavaContext.CLASS_BODY.check()?.let { return it }
                null
            }

            KotlinContext.CLASS_BODY -> JavaContext.CLASS_BODY.check()
            KotlinContext.IN_BLOCK -> JavaContext.IN_BLOCK.check()
            KotlinContext.EXPRESSION -> JavaContext.EXPRESSION.check()
        }
    }

    private fun isParsedAsJavaCode(text: String, javaContext: JavaContext, project: Project): Boolean {
        fun isParsedAsJavaFile(fileText: String) = isParsedAsFile(fileText, JavaFileType.INSTANCE, project)

        return when (javaContext) {
            JavaContext.TOP_LEVEL -> isParsedAsJavaFile(text)
            JavaContext.CLASS_BODY -> isParsedAsJavaFile("class Dummy { $text\n}")
            JavaContext.IN_BLOCK -> isParsedAsJavaFile("class Dummy { void foo() {$text\n}\n}")
            JavaContext.EXPRESSION -> isParsedAsJavaFile("class Dummy { Object field = $text; }")
        }
    }

    private fun isParsedAsKotlinCode(text: String, kotlinContext: KotlinContext, project: Project): Boolean {
        fun isParsedAsKotlinFile(fileText: String) = isParsedAsFile(fileText, KotlinFileType.INSTANCE, project)

        return when (kotlinContext) {
            KotlinContext.TOP_LEVEL -> isParsedAsKotlinFile(text)
            KotlinContext.CLASS_BODY -> isParsedAsKotlinFile("class Dummy { $text\n}")
            KotlinContext.IN_BLOCK -> isParsedAsKotlinFile("fun foo() {$text\n}")
            KotlinContext.EXPRESSION -> isParsedAsKotlinFile("val v = $text")
        }
    }

    private fun isParsedAsFile(fileText: String, fileType: LanguageFileType, project: Project): Boolean {
        val psiFile = parseAsFile(fileText, fileType, project)
        if (psiFile.anyDescendantOfType<PsiErrorElement>()) return false

        // Java 21 allows using implicitly declared classes.
        // Previously, a Java file like `class { void foo(){} }` was considered an error.
        // Starting from Java 21, it is parsed as a valid Java file.
        val isJavaFileWithImplicitClass = psiFile is PsiJavaFile && psiFile.classes.any { it is PsiImplicitClass }
        return !isJavaFileWithImplicitClass
    }

    private fun parseAsFile(fileText: String, fileType: LanguageFileType, project: Project): PsiFile {
        return PsiFileFactory.getInstance(project)
            .createFileFromText("Dummy." + fileType.defaultExtension, fileType, fileText, LocalTimeCounter.currentTime(), true)
    }

    /**
     * Prepares the [CopiedJavaCode] instance to convert with a specially constructed source Java file.
     * The file may consist of a dummy class, a container method, and member/local declaration stubs
     * extracted from the target Kotlin file (see [JavaContextDeclarationRenderer]).
     *
     * We relocate the copy-pasted Java code into this file to enhance the context of J2K conversion.
     */
    private fun prepareCopiedJavaCodeByContext(text: String, javaContext: JavaContext, target: KtElement): CopiedJavaCode {
        val targetPackage = (target.containingFile as KtFile).packageDirective?.text ?: ""
        val templateHeader = if (targetPackage.isNotEmpty()) "$targetPackage;\n" else ""

        val dummyContainingClassHeader = if (javaContext == JavaContext.TOP_LEVEL) {
            ""
        } else {
            val lightClass = target.getParentOfType<KtClass>(strict = false)?.toLightClass()
            val className = lightClass?.name ?: "Dummy"
            val extendsClause = lightClass?.getExtendsClause() ?: ""
            val implementsClause = lightClass?.getImplementsClause() ?: ""
            "class $className$extendsClause$implementsClause"
        }

        val (localDeclarations, memberDeclarations) = JavaContextDeclarationRenderer.render(target)
        val templateBody = when (javaContext) {
            JavaContext.TOP_LEVEL -> "$"
            JavaContext.CLASS_BODY -> "$dummyContainingClassHeader {\n$memberDeclarations $\n}"
            JavaContext.IN_BLOCK -> "$dummyContainingClassHeader {\n$memberDeclarations void foo() {\n$localDeclarations $\n}\n}"
            JavaContext.EXPRESSION -> "$dummyContainingClassHeader {\nObject field = $\n}"
        }

        val template = "$templateHeader$templateBody"
        val index = template.indexOf("$")
        assert(index >= 0)

        val fileText = template.substring(0, index) + text + template.substring(index + 1)
        val startOffsets = intArrayOf(index)
        val endOffsets = intArrayOf(index + text.length)

        return CopiedJavaCode(fileText, startOffsets, endOffsets)
    }

    private fun KtLightClass.getExtendsClause(): String {
        val types = extendsListTypes.joinToString { it.getCanonicalText(/* annotated = */ true) }
        return if (types.isNotEmpty()) " extends $types" else ""
    }

    private fun KtLightClass.getImplementsClause(): String {
        val types = implementsListTypes.joinToString { it.getCanonicalText(/* annotated = */ true) }
        return if (types.isNotEmpty()) " implements $types" else ""
    }

    object Util {
        @get:TestOnly
        var conversionPerformed: Boolean = false
    }
}