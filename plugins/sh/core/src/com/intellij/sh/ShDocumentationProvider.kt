// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.path.EelPath.Companion.parse
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.where
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.sh.psi.ShGenericCommandDirective
import com.intellij.sh.psi.ShLiteral
import com.intellij.sh.statistics.ShCounterUsagesCollector
import com.intellij.util.SuspendingLazy
import com.intellij.util.io.URLUtil
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import kotlin.io.path.Path

private const val TIMEOUT_IN_MILLISECONDS = 3 * 1000
private val LOG = Logger.getInstance(ShDocumentationProvider::class.java)

private fun wordWithDocumentation(o: PsiElement?): Boolean {
  return o is LeafPsiElement
         && o.elementType === ShTypes.WORD && (o.parent is ShLiteral)
         && (o.parent.getParent() is ShGenericCommandDirective)
}

private fun wrapIntoHtml(s: String?): String? {
  if (s == null) return null

  val sb: @NonNls StringBuilder = StringBuilder("<html><body><pre>")
  try {
    val m: @NonNls Matcher = URLUtil.URL_PATTERN.matcher(StringUtil.escapeXmlEntities(s))
    while (m.find()) {
      if (m.groupCount() > 0) {
        val url = m.group(0)
        m.appendReplacement(sb, HtmlChunk.link(url, url).toString())
      }
    }
    m.appendTail(sb)
  }
  catch (e: Exception) {
    LOG.warn(e)
  }
  sb.append("</pre></body></html>")
  return sb.toString()
}

internal class ShDocumentationProvider(private val scope: CoroutineScope) : DocumentationProvider {
  override fun generateDoc(o: PsiElement?, originalElement: PsiElement?): @NlsSafe String? {
    if (o == null) return null
    if (!wordWithDocumentation(o)) return null

    ShCounterUsagesCollector.DOCUMENTATION_PROVIDER_USED_EVENT_ID.log()
    return wrapIntoHtml(fetchInfo(o.getText(), o.getProject()))
  }

  override fun getCustomDocumentationElement(
    editor: Editor,
    file: PsiFile,
    contextElement: PsiElement?,
    targetOffset: Int,
  ): PsiElement? {
    val node = contextElement?.getNode()
    if (node == null || (TreeUtil.isWhitespaceOrComment(node) || node.getElementType() === ShTypes.LINEFEED)) {
      val at = if (targetOffset > 0) file.findElementAt(targetOffset - 1) else null
      if (wordWithDocumentation(at)) return at
    }
    return contextElement
  }

  private val myManExecutableCache = ConcurrentHashMap<EelMachine, SuspendingLazy<String?>>()
  private val myManCache = ConcurrentHashMap<Pair<EelMachine, String>, SuspendingLazy<String>>()

  private fun fetchInfo(commandName: String?, project: Project): @NlsSafe String? {
    val eelDescriptor = project.getEelDescriptor()

    if (commandName == null) return null
    val manExecutablePromise = myManExecutableCache.computeIfAbsent(eelDescriptor.machine) {
      scope.suspendingLazy {
        val eel = eelDescriptor.toEelApi()
        val path = eel.exec.fetchLoginShellEnvVariables()["PATH"]

        if (path != null) {
          for (dir in StringUtil.tokenize(path, eelDescriptor.osFamily.pathSeparator)) {
            val eelDir = runCatching { parse(dir, eelDescriptor) }.getOrNull() ?: continue
            val file = eelDir.resolve("info").asNioPath()

            if (Files.isExecutable(file)) {
              return@suspendingLazy file.toAbsolutePath().toString()
            }
          }
        }

        eel.exec.where("man")?.toString()
      }
    }

    return runBlockingMaybeCancellable { // fixme: is this good idea call blocking code here?
      myManCache.computeIfAbsent(eelDescriptor.machine to commandName) {
        scope.suspendingLazy {
          val manExecutable = manExecutablePromise.getValue()

          if (manExecutable == null) return@suspendingLazy ShBundle.message("error.message.can.t.find.info.in.your.path")

          val commandLine = GeneralCommandLine(manExecutable)
            .withWorkingDirectory(project.basePath?.let(::Path))
            .withParameters(commandName)

          val output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_IN_MILLISECONDS)
          if (output.getExitCode() != 0) output.stderr else output.stdout
        }
      }.getValue()
    }
  }
}
