// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewDocumentVersion
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet

/**
 * Holds the editor elements for one backend document state.
 *
 * The elements are sorted by source start offset.
 * The reconciler uses this model to create and remove editor folds.
 */
internal data class MarkdownLivePreviewPresentation(
  val documentVersion: MarkdownLivePreviewDocumentVersion,
  val elements: List<MarkdownLivePreviewElementPresentation>,
) {
  /** The largest source range. The reconciler uses this value to bound caret lookup. */
  val maxElementLength = elements.maxOfOrNull { it.spec.range.length } ?: 0
}

/**
 * Links one backend element to the folds that hide its source.
 *
 * The presentation factory creates this value from one backend spec.
 * Its folds describe every source range that the reconciler must hide.
 * The reconciler tracks each instance within one presentation.
 */
internal class MarkdownLivePreviewElementPresentation(
  val spec: MarkdownLivePreviewSpec,
  val folds: List<MarkdownLivePreviewFold>,
)

/** Describes a fold and the operations that the reconciler runs to mount it. */
internal interface MarkdownLivePreviewFold {
  val range: TextRange
  val decoration: MarkdownLivePreviewFoldDecoration? get() = null

  /** Checks whether the reconciler can retain an existing region for this presentation. */
  fun isSame(region: FoldRegion): Boolean

  /** Creates a region after obsolete folds are removed. The reconciler owns the returned region and runs the folding batch. */
  fun create(editor: EditorEx): FoldRegion?
}

/**
 * Converts backend elements to folds and manages editor resources for one element type.
 *
 * Implement this interface for every supported [MarkdownLivePreviewSpec] type.
 */
internal interface MarkdownLivePreviewElementRenderer : Disposable {
  /** Converts [spec] to the folds and decorations for one element. */
  fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold>

  /** Updates state that depends on the document before the factory creates new elements. */
  fun documentChanged()

  /**
   * Updates resources outside folds in the same editor update, after the reconciler has applied the fold changes.
   * A null presentation clears them.
   */
  fun reconcile(presentation: MarkdownLivePreviewPresentation?)

  /** Releases all resources registered by this renderer. */
  override fun dispose()
}

/**
 * Describes an editor resource that belongs to one source fold.
 *
 * Implement this interface when an element needs an inlay, highlighter, or similar resource.
 */
internal interface MarkdownLivePreviewFoldDecoration {
  /** Creates the resource after the reconciler creates [region]. Return null when creation fails. */
  fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration?
}

/**
 * Owns an editor resource after the reconciler mounts it.
 *
 * The reconciler updates or disposes this object when its source fold changes.
 */
internal interface MarkdownLivePreviewMountedDecoration : Disposable {
  /** Updates this resource for [decoration]. Return false when the reconciler must replace this object. */
  fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean

  /** Releases the editor resource owned by this object. */
  override fun dispose()
}

/** Adapts backend elements to editor presentations and owns resources that survive source reveal. */
internal class MarkdownLivePreviewPresentationFactory(project: Project, editor: EditorEx) : Disposable {
  private val elementRenderers: Map<Class<out MarkdownLivePreviewSpec>, MarkdownLivePreviewElementRenderer> = mapOf(
    MarkdownLivePreviewSpec.Conceal::class.java to MarkdownLivePreviewConcealRenderer(editor),
    MarkdownLivePreviewSpec.Bullet::class.java to MarkdownLivePreviewBulletRenderer(),
    MarkdownLivePreviewSpec.BlockQuote::class.java to MarkdownLivePreviewBlockQuoteRenderer(editor),
    MarkdownLivePreviewSpec.HorizontalRule::class.java to MarkdownLivePreviewHorizontalRuleRenderer(editor),
    MarkdownLivePreviewSpec.TaskCheckbox::class.java to MarkdownLivePreviewCheckboxRenderer(project, editor),
    MarkdownLivePreviewSpec.Image::class.java to MarkdownLivePreviewImageRenderer(project, editor),
    MarkdownLivePreviewSpec.Heading::class.java to MarkdownLivePreviewHeadingRenderer(editor),
  )

  init {
    elementRenderers.forEach { (_, renderer) -> Disposer.register(this, renderer) }
  }

  fun create(specs: MarkdownLivePreviewSpecSet): MarkdownLivePreviewPresentation {
    val elements = specs.elements.map { spec ->
      val folds = elementRenderers.getValue(spec::class.java).presentation(spec)
      MarkdownLivePreviewElementPresentation(spec, folds)
    }
    return MarkdownLivePreviewPresentation(specs.documentVersion, elements)
  }

  fun documentChanged() {
    elementRenderers.forEach { (_, renderer) -> renderer.documentChanged() }
  }

  fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    elementRenderers.forEach { (_, renderer) -> renderer.reconcile(presentation) }
  }

  override fun dispose() = Unit
}
