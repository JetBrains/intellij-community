// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.projectView.ProjectView
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.or
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.FakePsiElement
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.packaging.common.preferredProjectUrl
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.python.requirements.parser.psi.PackageName
import com.intellij.python.requirements.parser.psi.Path
import com.intellij.python.requirements.parser.psi.UrlReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Attaches the same Local/Remote ref to two hosts: `PackageName` owns the Ctrl-Click hot zone
 * on the name token; `NameReq` covers extras / version spec / marker so Quick Doc dispatch
 * (reference-driven) fires there too. Local module names → content root in the Project view;
 * anything else → upstream registry page (PyPI / Anaconda / custom repo) in a JCEF tab, URL
 * resolved at click time.
 */
class RequirementsReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      or(psiElement(NameReq::class.java), psiElement(PackageName::class.java), psiElement(Path::class.java)),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          // Bare local-path requirements (`./dist/pkg.tar.gz`, `/opt/wheels/pkg.whl`, `-e ./src`)
          // have no package name — resolve the path itself (relative paths against the requirements
          // file's directory) and navigate to it.
          if (element is Path) {
            val file = resolveLocalPath(element) ?: return PsiReference.EMPTY_ARRAY
            return arrayOf(LocalRequirementReference(element, TextRange(0, element.textLength), file))
          }

          // The requirement that owns this element: the NameReq itself, or the NameReq/UrlReq a
          // PackageName belongs to. `name @ url` requirements parse as UrlReq, not NameReq, so
          // without UrlReq here Ctrl-Click / Quick Doc on their package name did nothing.
          val requirement = when (element) {
                              is NameReq -> element
                              is PackageName -> element.parent as? NameReq ?: element.parent as? UrlReq
                              else -> null
                            } ?: return PsiReference.EMPTY_ARRAY

          val parsed = PyRequirementParser.fromLine(requirement.text, element.project) ?: return PsiReference.EMPTY_ARRAY
          // Skip NameReq when PackageName already covers the whole element (bare `requests` /
          // `some`) — otherwise both refs match the same offsets and trip "Choose Declaration".
          if (element is NameReq && element.packageName.textLength == element.textLength) return PsiReference.EMPTY_ARRAY
          val range = TextRange(0, element.textLength)
          val normalized = parsed.name

          // `pkg @ file:///path` points at a local artifact — resolve straight to that file/dir
          // (navigation handles project-tree vs. Finder) instead of falling through to PyPI.
          val localFile = resolveLocalFile(parsed.urlReference)
          if (localFile != null) {
            return arrayOf(LocalRequirementReference(element, range, localFile))
          }

          val moduleDir = ModuleManager.getInstance(element.project).modules
            .firstOrNull { PyPackageName.from(it.name).name == normalized }
            ?.let { ModuleRootManager.getInstance(it).contentRoots.firstOrNull() }

          if (moduleDir != null) {
            return arrayOf(LocalRequirementReference(element, range, moduleDir))
          }

          return arrayOf(RemoteRequirementReference(element, range, parsed.name))
        }
      }
    )
  }
}

/**
 * Resolves a `file:` URL from a `pkg @ file:///…` requirement to the local [VirtualFile] it points
 * at, or null when it is not a `file:` URL or the path does not exist.
 */
private fun resolveLocalFile(urlReference: String?): VirtualFile? {
  val url = urlReference ?: return null
  if (!url.startsWith("file:")) return null
  val path = try {
    Paths.get(URI(url))
  }
  catch (_: URISyntaxException) { // malformed URL
    return null
  }
  catch (_: IllegalArgumentException) { // URI is not absolute / has no path
    return null
  }
  catch (_: FileSystemNotFoundException) { // no provider for the URI scheme
    return null
  }
  return LocalFileSystem.getInstance().findFileByNioFile(path)
}

/**
 * Resolves a bare `path_req` path to the [VirtualFile] it points at. Absolute paths are used as-is;
 * relative paths are resolved against the requirements file's own directory (pip semantics), using
 * the top-level file so it also works inside injected `pyproject.toml` fragments.
 */
private fun resolveLocalPath(pathElement: PsiElement): VirtualFile? {
  val text = pathElement.text.trim()
  if (text.isEmpty()) return null
  val nioPath = try {
    Paths.get(text)
  }
  catch (_: InvalidPathException) {
    return null
  }
  val lfs = LocalFileSystem.getInstance()
  if (nioPath.isAbsolute) return lfs.findFileByNioFile(nioPath)

  val baseDir = InjectedLanguageManager.getInstance(pathElement.project)
                  .getTopLevelFile(pathElement)?.virtualFile?.parent ?: return null
  val resolved = try {
    baseDir.toNioPath().resolve(text).normalize()
  }
  catch (_: UnsupportedOperationException) { // base file has no local nio path
    return null
  }
  return lfs.findFileByNioFile(resolved)
}

private class LocalRequirementReference(
  element: PsiElement,
  range: TextRange,
  private val target: VirtualFile,
) : PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {
  // Wrap in FakePsiElement so Rename / Find Usages don't surface against the real file/directory.
  override fun resolve(): PsiElement = LocalRequirementNavTarget(element, target)
}

private class LocalRequirementNavTarget(
  private val anchor: PsiElement,
  private val target: VirtualFile,
) : FakePsiElement() {
  override fun getParent(): PsiElement = anchor

  override fun canNavigate(): Boolean = target.isValid

  // Both must be non-null: targetPresentation throws "… cannot be presented" otherwise.
  override fun getName(): String = target.name

  override fun getPresentableText(): String = target.name

  override fun navigate(requestFocus: Boolean) {
    if (!target.isValid) return
    val project = anchor.project
    val inProject = ProjectFileIndex.getInstance(project).isInContent(target)
    when {
      // Inside the project: reveal in the Project view (dirs) or open in the editor (files).
      inProject && target.isDirectory -> ProjectView.getInstance(project).select(/* element = */ null, target, requestFocus)
      inProject -> OpenFileDescriptor(project, target).navigate(requestFocus)
      // Outside the project: reveal in the OS file manager (Finder / Explorer).
      target.isDirectory -> RevealFileAction.openDirectory(target.toNioPath())
      else -> RevealFileAction.openFile(target.toNioPath())
    }
  }
}

private class RemoteRequirementReference(
  element: PsiElement,
  range: TextRange,
  private val packageName: String,
) : PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {
  override fun resolve(): PsiElement = RemoteRequirementNavTarget(element, packageName)
}

// URL resolution deferred to click time — `findPackageSpecification` suspends.
private class RemoteRequirementNavTarget(
  private val anchor: PsiElement,
  private val packageName: @NlsSafe String,
) : FakePsiElement() {
  // "Latest wins": holds the current navigation job. `AtomicReference.getAndSet` makes
  // swap-and-cancel safe against concurrent clicks; without it, two simultaneous navigate calls
  // could read the same previous job and clobber each other's assignment.
  private val currentNavigation: AtomicReference<Job?> = AtomicReference(null)

  override fun getParent(): PsiElement = anchor

  override fun canNavigate(): Boolean = true

  // Both must be non-null: targetPresentation throws "… cannot be presented" otherwise.
  override fun getName(): String = packageName

  override fun getPresentableText(): String = packageName

  override fun navigate(requestFocus: Boolean) {
    val project = anchor.project

    val sdk = getPythonSdk(anchor.containingFile)
    val packageManager = sdk?.let { PythonPackageManager.forSdk(project, it) }

    val newJob = PyPackageCoroutine.launch(project) {
      // Background progress so the user sees that the click did register; cancellable so a
      // long wait on init can be aborted from the status bar.
      withBackgroundProgress(project, PyBundle.message("python.requirements.resolve.package.page", packageName), cancellable = true) {
        val url = packageManager.resolvePackageUrl(packageName)

        // JCEF tab when available, system browser as a fallback (headless / JCEF-stripped runtimes).
        if (JBCefApp.isSupported()) {
          withContext(Dispatchers.EDT) {
            HTMLEditorProvider.openEditor(project, packageName, HTMLEditorProvider.Request.url(url))
          }
        }
        else {
          BrowserUtil.browse(url, project)
        }
      }
    }

    currentNavigation.getAndSet(newJob)?.cancel()
  }

  // Priority: METADATA Project-URL match > spec-resolved repository URL > PyPI fallback.
  private suspend fun PythonPackageManager?.resolvePackageUrl(packageName: String): String {
    if (this == null) return PyPiPackageRepository.getProjectUrl(packageName).url

    val installedPackageMetadata = listInstalledPackagesMetadata()[PyPackageName.from(packageName)]
    val preferredUpstream = installedPackageMetadata?.preferredProjectUrl()?.url
    if (preferredUpstream != null) return preferredUpstream

    val normalized = PyPackageName.from(packageName).name
    val installed = listInstalledPackages().firstOrNull { PyPackageName.from(it.name).name == normalized }
    val repository = findPackageSpecification(installed?.name ?: packageName, installed?.version)?.repository
                     ?: PyPiPackageRepository
    val effectiveRepo = if (installed is CondaPackage && !installed.installedWithPip) CondaPackageRepository else repository
    return (effectiveRepo.getProjectUrl(packageName) ?: PyPiPackageRepository.getProjectUrl(packageName)).url
  }
}
