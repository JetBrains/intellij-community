// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.projectView.ProjectView
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.DEFAULT_PROJECT_URL_LABEL
import com.jetbrains.python.packaging.common.ProjectUrl
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.common.preferredProjectUrl
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.requirements.psi.NameReq
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Quick Doc / Ctrl-hover for `requirements.txt` and Requirements injections in `pyproject.toml`.
 * Shows summary, license, Python spec, project URLs, and a registry link — local module folder
 * (workspace member or `pip install -e .` of an in-project module), Anaconda, PyPI, or a
 * custom-configured repo. Falls back to PyPI when the spec lookup yields no usable URL.
 */
class RequirementsDocumentationProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    val anchor = originalElement ?: element
    val requirementsFile = (anchor.containingFile as? RequirementsFile) ?: return null
    val nameReq = PsiTreeUtil.getParentOfType(anchor, NameReq::class.java, false) ?: return null
    val parsed = PyRequirementParser.fromLine(nameReq.text) ?: return null

    return RequirementDocumentationTarget(anchor.project, requirementsFile, parsed, nameReq)
  }
}

internal class RequirementDocumentationTarget(
  internal val project: Project,
  private val requirementsFile: RequirementsFile,
  private val pyRequirement: PyRequirement,
  anchor: PsiElement,
) : DocumentationTarget {

  private val anchorPointer: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(anchor)

  override fun createPointer(): Pointer<out DocumentationTarget> = Pointer {
    val anchor = anchorPointer.element ?: return@Pointer null
    RequirementDocumentationTarget(project, requirementsFile, pyRequirement, anchor)
  }

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(pyRequirement.name)
      .icon(AllIcons.Nodes.PpLib)
      .presentation()

  /**
   * Per-hover, sync — only in-memory snapshots, no suspending lookups.
   */
  override fun computeDocumentationHint(): @NlsContexts.HintText String {
    val packageManager = getPythonSdk(requirementsFile)?.let { PythonPackageManager.forSdk(project, it) }
    val packageName = pyRequirement.name
    val packageEscapedName = packageName.escapeXml()

    val isLocal = ModuleManager.getInstance(project).modules.any { PyPackageName.from(it.name).name == packageName }

    val installed = packageManager?.listInstalledPackagesSnapshot()?.firstOrNull { it.name == packageName }
    val metadata = installed?.let { packageManager.listInstalledPackagesMetadataSnapshot()[PyPackageName.from(it.name)] }
    val metadataSummary = metadata?.summary?.takeIf { it.isNotBlank() }
    val destinationLabel = metadata?.preferredProjectUrl()?.label ?: DEFAULT_PROJECT_URL_LABEL

    return when {
      isLocal -> {
        PyBundle.message("DOC.requirement.hint.local", packageEscapedName)
      }
      metadataSummary != null -> {
        PyBundle.message("DOC.requirement.hint.installedWithMetadata", packageEscapedName, metadataSummary.escapeXml(), destinationLabel)
      }
      installed != null -> {
        PyBundle.message("DOC.requirement.hint.installedWithVersion", packageEscapedName, installed.version.escapeXml(), destinationLabel)
      }
      else -> {
        PyBundle.message("DOC.requirement.hint.notInstalled", packageEscapedName, destinationLabel)
      }
    }
  }

  /**
   * Async because findPackageSpecification suspends (cached-index walk, not network).
   */
  override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
    val sdk = readAction { getPythonSdk(requirementsFile) }
    val packageManager = sdk?.let { PythonPackageManager.forSdk(project, it) }
    val packageName = pyRequirement.name
    val installed = packageManager?.listInstalledPackages()?.firstOrNull { it.name == packageName }
    // Names matching a project module are local packages — mirrors NonModulePackageName.create.
    val localPackagePath = readAction {
      ModuleManager.getInstance(project).modules
        .firstOrNull { PyPackageName.from(it.name).name == packageName }
        ?.let { ModuleRootManager.getInstance(it).contentRoots.firstOrNull()?.toNioPath() }
    }

    val repository = packageManager?.findPackageSpecification(installed?.name ?: packageName, installed?.version)?.repository
                     ?: PyPiPackageRepository
    // METADATA only for installed remote packages: nothing to read for non-installed names,
    // and local packages prefer project sources over the editable-install dist-info.
    val metadata = if (installed != null && localPackagePath == null) {
      packageManager.listInstalledPackagesMetadata()[PyPackageName.from(packageName)]
    }
    else null

    val html = renderHtml(installed, repository, localPackagePath, metadata)
    DocumentationResult.documentation(html)
  }

  private fun renderHtml(
    installed: PythonPackage?,
    repository: PyPackageRepository,
    localPackagePath: Path?,
    metadata: PythonPackageMetadata?,
  ): @NlsSafe String = buildString {
    // JEditorPane: <p> carries a ~14px bottom margin and body has its own padding, so we
    // render inline content with <br> separators and zero the body margins.
    append("<html><body style=\"margin:0;padding:0\">")

    val summary = metadata?.summary?.takeIf { it.isNotBlank() }
    val projectUrls = metadata?.projectUrls?.entries?.toList().orEmpty()
    val link = packageLink(installed, repository, localPackagePath)

    if (summary != null) {
      append(summary.escapeXml())
      append("<br><br>")
    }

    var first = true
    fun newRow() {
      if (first) first = false else append("<br>")
    }

    // <nobr> stops JEditorPane from breaking inside "Python: >=3.11" at the >= boundary.
    val metaParts = listOfNotNull(
      metadata?.license?.takeIf { it.isNotBlank() }?.let {
        "<nobr>License: ${formatLicenseExpression(it).escapeXml()}</nobr>"
      },
      metadata?.requiresPython?.takeIf { it.isNotBlank() }?.let {
        "<nobr>Python: ${it.escapeXml()}</nobr>"
      },
    )
    if (metaParts.isNotEmpty()) {
      newRow()
      append(metaParts.joinToString(" · "))
    }

    if (projectUrls.isNotEmpty()) {
      newRow()
      projectUrls.forEachIndexed { i, (label, url) ->
        if (i > 0) append(" · ")
        appendExternalLink(wrapBrowseUrl(url), label)
      }
    }

    when {
      link == null -> {
        newRow()
        append("Local package (editable install)")
      }
      localPackagePath != null -> {
        newRow()
        append("Local package ")
        append("<a href=\"").append(link.url).append("\">").append(link.label.escapeXml()).append("</a>")
      }
      // Skip the registry link when METADATA contributed upstream URLs above (more useful than the generic landing page).
      projectUrls.isEmpty() -> {
        newRow()
        appendExternalLink(wrapBrowseUrl(link.url), link.label)
      }
    }
    append("</body></html>")
  }

  // Icon must live inside the <a> so it inherits link styling and stays attached when the row wraps.
  private fun StringBuilder.appendExternalLink(url: String, label: String) {
    append("<a href=\"").append(url).append("\">")
    append(label.escapeXml())
    append(EXTERNAL_LINK_ICON)
    append("</a>")
  }

  // Priority: local folder > editable-install-no-folder (null) > Conda > PyPI > custom repo > PyPI fallback.
  private fun packageLink(installed: PythonPackage?, repository: PyPackageRepository, localFolder: Path?): ProjectUrl? {
    if (localFolder != null) {
      val label = "${localFolder.fileName ?: localFolder}/"
      val url = LOCAL_FOLDER_PREFIX + URLEncoder.encode(localFolder.toString(), StandardCharsets.UTF_8)
      return ProjectUrl(label, url)
    }
    if (installed?.isEditableMode == true) return null
    // A Conda-installed package always points at Anaconda regardless of which repository the
    // spec lookup resolved (the package may have been installed by Conda but the cached spec
    // search returned a different repo for the same name).
    val effectiveRepo = if (installed is CondaPackage && !installed.installedWithPip) CondaPackageRepository else repository
    return effectiveRepo.getProjectUrl(pyRequirement.name)
           ?: PyPiPackageRepository.getProjectUrl(pyRequirement.name)
  }

  // Flatten SPDX boolean: AND → ", ", OR → " or ". WITH (license-with-exception) stays as-is.
  private fun formatLicenseExpression(expression: String): String =
    expression
      .replace(Regex("\\s+AND\\s+"), ", ")
      .replace(Regex("\\s+OR\\s+"), " or ")

  private fun String.escapeXml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/**
 * Routes our two link schemes:
 *  - `psi_element://py_doc_localdir/<path>` → select folder in the Project view.
 *  - `psi_element://py_doc_browse/<url>` → open in a JCEF editor tab (or system browser if JCEF is unavailable).
 *
 * The `psi_element://` prefix stops the platform from auto-routing to `BrowserUtil.browse`.
 * Returning `null` leaves the doc browser in place; the side effect is scheduled on EDT.
 */
internal class RequirementDocumentationLinkHandler : DocumentationLinkHandler {
  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
    val project = (target as? RequirementDocumentationTarget)?.project ?: return null
    when {
      url.startsWith(LOCAL_FOLDER_PREFIX) -> {
        val path = URLDecoder.decode(url.removePrefix(LOCAL_FOLDER_PREFIX), StandardCharsets.UTF_8)
        ApplicationManager.getApplication().invokeLater {
          val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(path))
                            ?: return@invokeLater
          ProjectView.getInstance(project).select(null, virtualFile, /* requestFocus = */ true)
          // No targeted "close doc popup" API — close the global popup stack (the doc popup is the only one visible).
          IdeEventQueue.getInstance().popupManager.closeAllPopups(/* forceRestoreFocus = */ false)
        }
        return null
      }
      url.startsWith(BROWSE_URL_PREFIX) -> {
        val target = URLDecoder.decode(url.removePrefix(BROWSE_URL_PREFIX), StandardCharsets.UTF_8)
        ApplicationManager.getApplication().invokeLater {
          if (JBCefApp.isSupported()) {
            HTMLEditorProvider.openEditor(project, target, HTMLEditorProvider.Request.url(target))
          }
          else {
            BrowserUtil.browse(target, project)
          }
          IdeEventQueue.getInstance().popupManager.closeAllPopups(/* forceRestoreFocus = */ false)
        }
        return null
      }
      else -> return null
    }
  }
}

private const val LOCAL_FOLDER_PREFIX: String = DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "py_doc_localdir/"
private const val BROWSE_URL_PREFIX: String = DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "py_doc_browse/"

// Same `<icon …/>` markup Quick Doc uses to inline AllIcons references in HTML.
private const val EXTERNAL_LINK_ICON: String = "<icon src=\"AllIcons.Ide.External_link_arrow\"/>"

// Routes the click into RequirementDocumentationLinkHandler (JCEF tab) instead of BrowserUtil.
private fun wrapBrowseUrl(url: String): String = BROWSE_URL_PREFIX + URLEncoder.encode(url, StandardCharsets.UTF_8)
