package org.jetbrains.yaml.smart

import com.intellij.injected.editor.InjectionMeta
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLLanguage

val INJECTION_RANGE_BEFORE_ENTER = Key.create<RangeMarker>("NEXT_ELEMENT")
val INDENT_BEFORE_PROCESSING = Key.create<String>("INDENT_BEFORE_PROCESSING")

fun preserveIndentStateBeforeProcessing(file: PsiFile, dataContext: DataContext) {
  if (file.virtualFile !is VirtualFileWindow) return
  val hostEditor = CommonDataKeys.HOST_EDITOR.getData(dataContext) as? EditorEx ?: return
  val hostFile = PsiManager.getInstance(hostEditor.project ?: return).findFile(hostEditor.virtualFile ?: return) ?: return
  if (!hostFile.viewProvider.hasLanguage(YAMLLanguage.INSTANCE)) return

  val injectionHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file) ?: return
  val lineIndent = InjectionMeta.getInjectionIndent()[injectionHost]
  INDENT_BEFORE_PROCESSING[file] = lineIndent
  INJECTION_RANGE_BEFORE_ENTER[file] = hostEditor.document.createRangeMarker(injectionHost.textRange)
}
