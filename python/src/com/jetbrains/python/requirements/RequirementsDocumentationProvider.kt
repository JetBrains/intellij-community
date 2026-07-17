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
import com.intellij.openapi.projectRoots.Sdk
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
import com.jetbrains.python.packaging.common.ProjectUrl
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
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
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

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
  private val requirementsFile: RequirementsFile?,
  private val pyRequirement: PyRequirement,
  anchor: PsiElement?,
  private val sdkOverride: Sdk? = null,
) : DocumentationTarget {

  private val anchorPointer: SmartPsiElementPointer<PsiElement>? = anchor?.let { SmartPointerManager.createPointer(it) }

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val anchorPointer = this.anchorPointer ?: return Pointer.hardPointer(this)
    return Pointer {
      val anchor = anchorPointer.element ?: return@Pointer null
      RequirementDocumentationTarget(project, requirementsFile, pyRequirement, anchor, sdkOverride)
    }
  }

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(pyRequirement.name)
      .icon(AllIcons.Nodes.PpLib)
      .presentation()

  /**
   * Per-hover, sync — only in-memory snapshots, no suspending lookups. All the decision logic
   * lives in [computeRequirementHint] (unit-tested); this method just wires the current SDK's
   * snapshots and formats the chosen variant through [PyBundle].
   */
  override fun computeDocumentationHint(): @NlsContexts.HintText String {
    val sdk = sdkOverride ?: requirementsFile?.let { getPythonSdk(it) }
    val packageManager = sdk?.let { PythonPackageManager.forSdk(project, it) }
    val packageName = pyRequirement.name

    val isLocal = ModuleManager.getInstance(project).modules.any { PyPackageName.from(it.name).name == packageName }
    val installed = packageManager?.listInstalledPackagesSnapshot()?.firstOrNull { it.name == packageName }
    val metadata = installed?.let { packageManager.listInstalledPackagesMetadataSnapshot()[PyPackageName.from(it.name)] }

    return renderHint(computeRequirementHint(packageName, isLocal, installed, metadata))
  }

  /**
   * `escapeXml` is required because the bundle values are HTML templates (`<b>{0}</b><br>...`).
   * `hint.packageName` comes from user-authored `requirements.txt`; `hint.summary` / `hint.version`
   * come from installed METADATA. Both are external, untrusted data — without escaping, a name
   * like `<script>` or `<img onerror=…>` would inject markup into the JEditorPane hint (XSS).
   * Escaping renders angle brackets as literal `&lt;` / `&gt;` text.
   */
  private fun renderHint(hint: RequirementHint): @NlsContexts.HintText String {
    val escapedName = hint.packageName.escapeXml()
    return when (hint) {
      is RequirementHint.Local ->
        PyBundle.message("DOC.requirement.hint.local", escapedName)
      is RequirementHint.InstalledWithMetadata ->
        PyBundle.message("DOC.requirement.hint.installedWithMetadata", escapedName, hint.summary.escapeXml(), hint.destinationLabel)
      is RequirementHint.InstalledWithVersion ->
        PyBundle.message("DOC.requirement.hint.installedWithVersion", escapedName, hint.version.escapeXml(), hint.destinationLabel)
      is RequirementHint.NotInstalled ->
        PyBundle.message("DOC.requirement.hint.notInstalled", escapedName, hint.destinationLabel)
    }
  }

  /**
   * Async because findPackageSpecification suspends (cached-index walk, not network).
   */
  override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
    val sdk = sdkOverride ?: readAction { requirementsFile?.let { getPythonSdk(it) } }
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

    // For not-installed names we still want a hover with a PyPI link, so synthesise a minimal
    // [PythonPackage] from the requirement name — `installed` becomes the single source of truth
    // for both the rendered text and the link lookup.
    val pkg = installed ?: PythonPackage(name = packageName, version = "", isEditableMode = false)
    val html = renderHtml(pkg, repository, localPackagePath, metadata)
    DocumentationResult.documentation(html)
  }

  /**
   * Composes the Quick Doc HTML for a requirement hover. External strings (package summary,
   * license expression, requires-python spec, project-URL labels, link labels, editable-install
   * path) go through [HtmlBuffer.text] which escapes them; layout tags and pre-safe URLs go
   * through [HtmlBuffer.raw]. Keeping the two paths distinct at every call site is what Ilya's
   * review asked for — double-escapes and missed-escapes both become visible mistakes instead
   * of quiet XSS vectors in the JEditorPane popup.
   */
  private fun renderHtml(
    installed: PythonPackage,
    repository: PyPackageRepository,
    localPackagePath: Path?,
    metadata: PythonPackageMetadata?,
  ): @NlsSafe String {
    val buf = HtmlBuffer()
    // JEditorPane: <p> carries a ~14px bottom margin and body has its own padding, so we
    // render inline content with <br> separators and zero the body margins.
    buf.raw("<html><body style=\"margin:0;padding:0\">")

    val summary = metadata?.summary?.takeIf { it.isNotBlank() }
    // safeProjectUrls, not the raw map: unsafe-scheme URLs (e.g. javascript:) must never become a
    // clickable link that RequirementDocumentationLinkHandler would hand to HTMLEditorProvider (PY-90871).
    val projectUrls = metadata?.safeProjectUrls?.entries?.toList().orEmpty()
    val link = packageLink(installed, repository, localPackagePath)

    if (summary != null) {
      buf.text(summary).raw("<br><br>")
    }

    var first = true
    fun newRow() {
      if (first) first = false else buf.raw("<br>")
    }

    // <nobr> stops JEditorPane from breaking inside "Python: >=3.11" at the >= boundary.
    val licenseLabel = metadata?.license?.takeIf { it.isNotBlank() }?.let(::formatLicenseExpression)
    val pythonSpec = metadata?.requiresPython?.takeIf { it.isNotBlank() }
    if (licenseLabel != null || pythonSpec != null) {
      newRow()
      if (licenseLabel != null) {
        buf.raw("<nobr>License: ").text(licenseLabel).raw("</nobr>")
      }
      if (pythonSpec != null) {
        if (licenseLabel != null) buf.raw(" · ")
        buf.raw("<nobr>Python: ").text(pythonSpec).raw("</nobr>")
      }
    }

    if (projectUrls.isNotEmpty()) {
      newRow()
      projectUrls.forEachIndexed { i, (label, url) ->
        if (i > 0) buf.raw(" · ")
        buf.appendExternalLink(wrapBrowseUrl(url), label)
      }
    }

    when {
      link == null -> {
        newRow()
        buf.text("Local package (editable install)")
      }
      localPackagePath != null -> {
        newRow()
        // link.url is our own `psi_element://py_doc_localdir/<url-encoded-path>` — safe to embed raw.
        buf.text("Local package ").raw("<a href=\"").raw(link.url).raw("\">").text(link.label).raw("</a>")
      }
      // Skip the registry link when METADATA contributed upstream URLs above (more useful than the generic landing page).
      projectUrls.isEmpty() -> {
        newRow()
        buf.appendExternalLink(wrapBrowseUrl(link.url), link.label)
      }
    }
    val editableLocation = installed.editableLocation
    if (editableLocation != null) {
      newRow()
      val display = if (editableLocation.scheme == "file") {
        try {
          Paths.get(editableLocation).pathString
        }
        catch (_: InvalidPathException) {
          editableLocation.toString()
        }
      }
      else {
        editableLocation.toString()
      }
      buf.text(display)
    }
    buf.raw("</body></html>")
    return buf.toString()
  }

  // Icon must live inside the <a> so it inherits link styling and stays attached when the row wraps.
  // `url` here is already the `psi_element://py_doc_browse/<url-encoded>` wrapper — safe to embed raw.
  private fun HtmlBuffer.appendExternalLink(url: String, label: String) {
    raw("<a href=\"").raw(url).raw("\">").text(label).raw(EXTERNAL_LINK_ICON).raw("</a>")
  }

  // Priority: local folder > editable-install-no-folder (null) > Conda > PyPI > custom repo > PyPI fallback.
  private fun packageLink(installed: PythonPackage, repository: PyPackageRepository, localFolder: Path?): ProjectUrl? {
    if (localFolder != null) {
      val label = "${localFolder.fileName ?: localFolder}/"
      val url = LOCAL_FOLDER_PREFIX + URLEncoder.encode(localFolder.toString(), StandardCharsets.UTF_8)
      return ProjectUrl(label, url)
    }
    if (installed.isEditableMode) return null
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
 * HTML string builder with a compile-visible escape boundary — [text] escapes XML entities,
 * [raw] appends verbatim. Splitting the two APIs stops the double-escape / missed-escape mistake
 * Ilya's review flagged: at every call site you have to pick which one to invoke, so the
 * escaping story is explicit at every point instead of relying on convention.
 */
private class HtmlBuffer {
  private val buffer = StringBuilder()

  fun text(value: String): HtmlBuffer {
    buffer.append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
    return this
  }

  fun raw(value: String): HtmlBuffer {
    buffer.append(value)
    return this
  }
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
