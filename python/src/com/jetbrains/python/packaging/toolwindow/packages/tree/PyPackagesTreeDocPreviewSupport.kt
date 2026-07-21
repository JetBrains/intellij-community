// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.lang.documentation.ide.impl.DocumentationManager.DocumentationOnHoverSession
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageTreeCellRenderer
import com.jetbrains.python.requirements.RequirementDocumentationTarget
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Wires the packages tree to the platform documentation hover popup so a hover on a package row
 * shows the same "mini doc" that `requirements.txt` / `pyproject.toml` shows — with the same
 * `Cmd`+click link routing, the same "…" / Show Documentation tool-window action, etc.
 *
 * Independently re-implements the same pattern as `VcsLogGraphTableLinkPreviewSupport` from the
 * VCS plugin — no code is shared and the two are free to evolve separately; the reference is here
 * only so future readers know where to look for the platform-level recipe of "track the row under
 * the cursor, build a documentation target without PSI, hand it to
 * [DocumentationManager.showDocumentationOnHoverAroundByRequests]".
 */
internal class PyPackagesTreeDocPreviewSupport(private val tree: PyPackagesTree, private val project: Project) : MouseAdapter() {
  private var session: PreviewSession? = null

  override fun mouseMoved(e: MouseEvent) {
    val row = tree.getClosestRowForLocation(e.x, e.y).takeIf { it >= 0 } ?: run {
      session?.documentationSession?.mouseOutsideOfSourceArea()
      return
    }
    val bounds = tree.getRowBounds(row) ?: return
    if (e.y < bounds.y || e.y >= bounds.y + bounds.height) {
      session?.documentationSession?.mouseOutsideOfSourceArea()
      return
    }
    val packageName = when (val pkg = tree.packageAtRow(row)) {
      is InstalledPackage -> pkg.instance.name
      is RequirementPackage -> pkg.instance.name
      is InstallablePackage,
      is WorkspaceMember,
      is LoadingNode,
      is DependencyGroupNode,
      is UndeclaredPackagesGroup,
      null -> {
        session?.documentationSession?.mouseOutsideOfSourceArea()
        return
      }
    }
    if (!isHoverOverName(row, bounds, e.x)) {
      session?.documentationSession?.mouseOutsideOfSourceArea()
      return
    }
    val sdk = project.service<PyPackagingToolWindowService>().currentSdk ?: return
    session?.let { existing ->
      if (existing.row == row && existing.packageName == packageName) {
        existing.documentationSession.mouseWithinSourceArea()
        return
      }
      if (!existing.documentationSession.tryFinishImmediately()) return
    }
    val target = RequirementDocumentationTarget(
      project = project,
      requirementsFile = null,
      pyRequirement = pyRequirement(packageName),
      anchor = null,
      sdkOverride = sdk,
    )
    // showDocumentationOnHoverAround calls target.documentationRequest() inline (which asserts read
    // access); use the *ByRequests overload and resolve the request inside a read action ourselves.
    val request = runReadActionBlocking { target.documentationRequest() }
    val area = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
    val docSession = project.service<DocumentationManager>().showDocumentationOnHoverAroundByRequests(
      listOf(request), project, tree, area, /* minHeight = */ 350, /* delay = */ 200,
    ) {
      session = null
    } ?: return
    session = PreviewSession(row, packageName, docSession)
  }

  override fun mouseExited(e: MouseEvent?) {
    session?.documentationSession?.mouseOutsideOfSourceArea()
  }

  /**
   * The renderer paints the package name as fragment 0. Anything else (inline change-version icon,
   * trailing trash icon, version text, link link) sits at a different fragment, and we want hover
   * to fire strictly over the name — same fragment check the existing tooltip already uses.
   */
  private fun isHoverOverName(row: Int, bounds: Rectangle, screenX: Int): Boolean {
    val path = tree.getPathForRow(row) ?: return false
    val node = path.lastPathComponent as DefaultMutableTreeNode
    val renderer = tree.cellRenderer.getTreeCellRendererComponent(
      tree, node, tree.isPathSelected(path), tree.isExpanded(row), tree.model.isLeaf(node), row, tree.hasFocus(),
    ) as PyPackageTreeCellRenderer
    renderer.setSize(bounds.width, bounds.height)
    val relativeX = screenX - bounds.x
    val changeIconX = renderer.inlineChangeVersionIconX
    val changeIcon = renderer.inlineChangeVersionIcon
    if (changeIconX > 0 && changeIcon != null && relativeX in changeIconX..(changeIconX + changeIcon.iconWidth)) return false
    val trailingIconX = renderer.trailingIconX
    val trailingIcon = renderer.trailingIcon
    if (trailingIconX > 0 && trailingIcon != null && relativeX in trailingIconX..(trailingIconX + trailingIcon.iconWidth)) return false
    return renderer.findFragmentAt(relativeX) == 0
  }

  private data class PreviewSession(
    val row: Int,
    val packageName: String,
    val documentationSession: DocumentationOnHoverSession,
  )
}
