//package org.jetbrains.plugins.textmate.editor
//
//import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
//import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
//import com.intellij.openapi.actionSystem.DataContext
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.editor.ex.EditorEx
//import com.intellij.psi.PsiDocumentManager
//import com.intellij.psi.PsiFile
//import org.jetbrains.plugins.textmate.TextMateService
//import org.jetbrains.plugins.textmate.language.preferences.IndentAction
//import org.jetbrains.plugins.textmate.language.preferences.OnEnterRule
//import org.jetbrains.plugins.textmate.psi.TextMateFile
//
//// todo: consider using this for onEnterRules, instead of TextMateLineIndentProvider
//class TextMateEnterHandler : EnterHandlerDelegateAdapter() {
//  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
//    if (file !is TextMateFile) return super.postProcessEnter(file, editor, dataContext)
//
//    val caretOffset = editor.caretModel.offset
//    val manager = PsiDocumentManager.getInstance(editor.project!!)
//    manager.commitDocument(editor.document)
//    manager.getPsiFile(editor.document)
//
//    val actualScope = TextMateEditorUtils.getCurrentScopeSelector((editor as EditorEx)) ?: return super.postProcessEnter(file, editor,
//                                                                                                                         dataContext)
//
//    val registry = TextMateService.getInstance().preferenceRegistry
//    val preferencesList = registry.getPreferences(actualScope)
//    val onEnterRules = preferencesList.mapNotNull { it.onEnterRules }.flatten()
//
//    indent(editor, caretOffset, onEnterRules)
//
//    return super.postProcessEnter(file, editor, dataContext)
//  }
//
//  private fun indent(editor: Editor,
//                     caretOffset: Int,
//                     onEnterRules: List<OnEnterRule>) {
//    val document = editor.document
//    val text = document.text
//    val lines = text.lines()
//    val lineNumber = document.getLineNumber(caretOffset)
//    if (lineNumber <= 0L) return
//    val lineOffset = document.getLineStartOffset(lineNumber)
//    if (lineOffset <= 0L) return
//    val prevLineText = lines[lineNumber - 1]
//
//    for (onEnterRule in onEnterRules){
//      val beforeTextPatter = onEnterRule.beforeText.text
//      if (prevLineText.contains(Regex(beforeTextPatter))) {
//        when (onEnterRule.action.indent) {
//          // todo: use com.intellij.openapi.editor.actions.EditorActionUtil.indentLine(com.intellij.openapi.project.Project, com.intellij.openapi.editor.Editor, int, int, int)
//          IndentAction.INDENT -> editor.document.replaceString(lineOffset, lineOffset, "    ")
//          else -> {}
//        }
//      }
//    }
//  }
//}