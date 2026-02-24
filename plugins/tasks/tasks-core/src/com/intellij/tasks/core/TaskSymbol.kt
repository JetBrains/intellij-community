// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core

import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_GRAYED
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
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol.IjTextAttributesKeyProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.tasks.Task
import com.intellij.tasks.TaskBundle
import com.intellij.tasks.TaskState
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

private val TASKS_TASKS = PolySymbolKind["tasks", "tasks"]
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

  override val kind: PolySymbolKind
    get() = TASKS_TASKS

  override val name: @NlsSafe String
    get() = id

  override fun getDocumentationTarget(): DocumentationTarget =
    getDocumentationTarget(null)

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget =
    PolySymbolDocumentationTarget.create(this, null) { symbol, _ ->
      val task = symbol.task
      val taskUrl = symbol.taskUrl
      var definition = HtmlBuilder()
      if (taskUrl != null) {
        definition.append(HtmlChunk.tag("a").attr("href", taskUrl).addText(symbol.id))
        docUrl(taskUrl)
      }
      else {
        definition.append(symbol.id)
      }
      val propertiesToShowInPreview = task?.propertiesToShowInPreview
      val additionalProperties = task?.customProperties ?: emptyMap()
      val propsToShow = propertiesToShowInPreview?.mapNotNull { additionalProperties[it] }
                        ?: emptyList()

      if (task?.isClosed == true) {
        definition = HtmlBuilder().append(definition.wrapWith("s"))
      }
      definition.append(" ").append(task?.summary ?: TaskBundle.message("task.symbol.not.found"))
      task?.state
        ?.takeIf { it != TaskState.OTHER && propsToShow.isEmpty() }
        ?.let {
          @Suppress("HardCodedStringLiteral")
          definition.append(
            HtmlChunk.span().setClass(CLASS_GRAYED)
              .addRaw(" (${StringUtil.escapeXmlEntities(it.presentableName).replace(" ", "&nbsp;")})")
          )
        }
      definition(definition.toString())

      if (task == null) return@create
      @Nls
      val description = HtmlBuilder()

      @Suppress("HardCodedStringLiteral")
      if (propsToShow.isNotEmpty()) {
        description.appendRaw("<table colspan=0 style='width:100%'><tr>")
        propsToShow.forEach { property ->
          val columnTitle = property.displayName.let { StringUtil.capitalizeWords(it, true) }
            .let { if (!it.endsWith(":")) "$it:" else it }
          description.appendRaw("<td>${GRAYED_START}$columnTitle${GRAYED_END}")
        }
        description.appendRaw("<tr>")
        propsToShow.forEach {
          description.appendRaw("<td>${it.iconUrl?.let { url -> "<icon src='$url'></icon>&nbsp;" } ?: ""}${it.value}")
        }
        description.appendRaw("</table>")
      }

      // TODO add support for fetching images through the Task implementation
      val taskDescription = task.description?.removeImages()
      if (!taskDescription.isNullOrBlank()) {
        if (propsToShow.isNotEmpty())
          description.append(HtmlChunk.hr())
        if (taskDescription.length > 1500)
          description
            .appendRaw(DocMarkdownToHtmlConverter.convert(
              DefaultProjectFactory.getInstance().defaultProject,
              StringUtil.shortenTextWithEllipsis(taskDescription, 1500, 0, true),
              null)
            )
            .append(HtmlChunk.p().child(
              HtmlChunk.tag("a").attr("href", taskUrl ?: "")
                .addText(TaskBundle.message("task.symbol.doc.read.more")))
            )
        else
          description.appendRaw(
            DocMarkdownToHtmlConverter.convert(DefaultProjectFactory.getInstance().defaultProject, taskDescription)
          )
      }

      task.created?.let { descriptionSection(TaskBundle.message("task.preview.created"), DateFormatUtil.formatDateTime(it)) }
      task.updated?.let { descriptionSection(TaskBundle.message("task.preview.updated"), DateFormatUtil.formatDateTime(it)) }

      icon(task.icon)
      description(description.toString())
    }

  @PolySymbol.Property(IjTextAttributesKeyProperty::class)
  val ijTextAttributesKey: String
    get() = EditorColors.REFERENCE_HYPERLINK_COLOR.externalName

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      TASK_PROPERTY -> property.tryCast(task)
      else -> super.get(property)
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

  companion object {
    @Suppress("HardCodedStringLiteral")
    @Nls
    private fun String.removeImages(): String =
      replace(Regex("!\\[]\\([^)\t\n]+\\)(\\{[^} \t\n]+})?"), "")
        .replace(Regex("<img [^>]*>"), "")
  }
}