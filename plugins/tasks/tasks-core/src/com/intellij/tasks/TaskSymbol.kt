// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks

import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.DocumentationMarkup.GRAYED_END
import com.intellij.lang.documentation.DocumentationMarkup.GRAYED_START
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import org.jetbrains.annotations.Nls
import javax.swing.Icon

private val TASKS_TASKS = PolySymbolQualifiedKind["tasks", "tasks"]
val TASK_PROPERTY: PolySymbolProperty<Task> = PolySymbolProperty["task", Task::class.java]

class TaskSymbol(override val task: Task) : AbstractTaskSymbol(task)

class LazyTaskSymbol(id: String, taskUrl: String, private val taskFetcher: suspend () -> Task?) : AbstractTaskSymbol(id, taskUrl) {

  override val task: Task?
    get() = runBlockingCancellable { taskFetcher() }
}

sealed class AbstractTaskSymbol : PolySymbol, DocumentationSymbol {

  @NlsSafe
  private val id: String
  private val taskUrl: String?
  final override val icon: Icon?

  constructor(id: String, taskUrl: String) {
    this.id = id
    this.taskUrl = taskUrl
    icon = null
  }

  constructor(sourceTask: Task) {
    this.id = sourceTask.id
    this.taskUrl = sourceTask.issueUrl
    icon = sourceTask.icon
  }

  abstract val task: Task?

  override val origin: PolySymbolOrigin
    get() = PolySymbolOrigin.empty()

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = TASKS_TASKS

  override val name: @NlsSafe String
    get() = id

  val presentableId: @NlsSafe String
    get() = task?.presentableId ?: id

  override fun getDocumentationTarget(): DocumentationTarget =
    getDocumentationTarget(null)

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget =
    PolySymbolDocumentationTarget.create(this, null) { symbol, _ ->
      val task = symbol.task
      val taskUrl = symbol.taskUrl
      if (taskUrl != null) {
        definition("<a href='${taskUrl}'>${symbol.id}</a> ${task?.summary ?: "<not found>"}")
        docUrl(taskUrl)
      }
      else {
        definition("${symbol.id} ${task?.summary ?: "<not found>"}")
      }

      if (task == null) return@create
      @Nls
      val description = StringBuilder()

      description.append("<table colspan=0 style='width:100%'>")
      task.state?.let {
        description.append("<tr><td>${GRAYED_START}State:${GRAYED_END}<td>${it.presentableName}")
      }
      task.created?.let {
        description.append(
          "<tr><td>${GRAYED_START}Created at:${GRAYED_END}<td>${DateTimeFormatManager.getInstance().dateFormat.format(it)} ${DateFormatUtil.formatTime(it)}"
        )
      }
      task.updated?.let {
        description.append(
          "<tr><td>${GRAYED_START}Updated at:${GRAYED_END}<td>${DateTimeFormatManager.getInstance().dateFormat.format(it)} ${DateFormatUtil.formatTime(it)}"
        )
      }
      description.append("</table>")

      val taskDescription = task.description
      if (!taskDescription.isNullOrBlank())
        description.append("<hr>")

      if (!taskDescription.isNullOrBlank()) {
        if (taskDescription.length > 1500)
          description
            .append(DocMarkdownToHtmlConverter.convert(
              DefaultProjectFactory.getInstance().defaultProject, taskDescription.ellipsize(1500), null)
            )
            .append("<p><a href='${taskUrl}'>Read more...</a>")
        else
          description.append(DocMarkdownToHtmlConverter.convert(DefaultProjectFactory.getInstance().defaultProject, taskDescription))
      }
      description(description.toString())
    }

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      PolySymbol.PROP_IJ_TEXT_ATTRIBUTES_KEY -> property.tryCast(EditorColors.REFERENCE_HYPERLINK_COLOR.externalName)
      TASK_PROPERTY -> property.tryCast(task)
      else -> null
    }

  override val presentation: TargetPresentation
    get() = TargetPresentation.builder(task?.presentableName ?: id)
      .icon(icon)
      .presentation()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (taskUrl != null)
      listOf(
        object : NavigationTarget {
          override fun createPointer(): Pointer<NavigationTarget> = hardPointer(this)
          override fun computePresentation(): TargetPresentation = this@AbstractTaskSymbol.presentation
          override fun navigationRequest(): NavigationRequest? = object : Navigatable {
            override fun canNavigate(): Boolean = true
            override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(taskUrl)
          }.navigationRequest()
        })
    else
      emptyList()

  override fun createPointer(): Pointer<out AbstractTaskSymbol> =
    hardPointer(this)

  override fun hashCode(): Int = id.hashCode()

  override fun equals(other: Any?): Boolean =
    other === this || other is AbstractTaskSymbol && other.javaClass == javaClass && other.id == id
}

@NlsSafe
private fun String.ellipsize(maxLength: Int): String =
  if (length > maxLength) take(maxLength - 1) + "…" else take(maxLength)