// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.ide.BrowserUtil
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
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
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.preferredProjectUrl
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.SimpleName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Attaches the same Local/Remote ref to two hosts: `SimpleName` owns the Ctrl-Click hot zone
 * on the name token; `NameReq` covers extras / version spec / marker so Quick Doc dispatch
 * (reference-driven) fires there too. Local module names → content root in the Project view;
 * anything else → upstream registry page (PyPI / Anaconda / custom repo) in a JCEF tab, URL
 * resolved at click time.
 */
class RequirementsReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      or(psiElement(NameReq::class.java), psiElement(SimpleName::class.java)),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          val nameReq = when (element) {
                          is NameReq -> element
                          is SimpleName -> element.parent as? NameReq
                          else -> null
                        } ?: return PsiReference.EMPTY_ARRAY

          val parsed = PyRequirementParser.fromLine(nameReq.text) ?: return PsiReference.EMPTY_ARRAY
          // Skip NameReq when SimpleName already covers the whole element (bare `requests` /
          // `some`) — otherwise both refs match the same offsets and trip "Choose Declaration".
          if (element is NameReq && nameReq.name.textLength == element.textLength) return PsiReference.EMPTY_ARRAY
          val range = TextRange(0, element.textLength)
          val normalized = parsed.name

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

private class LocalRequirementReference(
  element: PsiElement,
  range: TextRange,
  private val folder: VirtualFile,
) : PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {
  // Wrap in FakePsiElement so Rename / Find Usages don't surface against the real directory.
  override fun resolve(): PsiElement = LocalRequirementNavTarget(element, folder)
}

private class LocalRequirementNavTarget(
  private val anchor: PsiElement,
  private val folder: VirtualFile,
) : FakePsiElement() {
  override fun getParent(): PsiElement = anchor

  override fun canNavigate(): Boolean = folder.isValid

  // Both must be non-null: targetPresentation throws "… cannot be presented" otherwise.
  override fun getName(): String = folder.name

  override fun getPresentableText(): String = folder.name

  override fun navigate(requestFocus: Boolean) {
    if (!folder.isValid) return
    if (folder.isDirectory) {
      ProjectView.getInstance(anchor.project).select(/* element = */ null, folder, requestFocus)
    }
    else {
      OpenFileDescriptor(anchor.project, folder).navigate(requestFocus)
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
