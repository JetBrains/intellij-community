// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.google.common.cache.Cache
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.PyInstallRequirementsFix
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.sdk.PythonSdkType
import javax.swing.JComponent

class PyStubPackagesAdvertiser : PyInspection() {

  companion object {
    // file-level suggestion will be shown for packages below
    private val FORCED = mapOf("django" to "Django", "numpy" to "numpy") // top-level package to package on PyPI

    // notification will be shown for packages below
    private val CHECKED = mapOf("coincurve" to "coincurve",
                                "docutils" to "docutils",
                                "ordered_set" to "ordered-set",
                                "gi" to "PyGObject",
                                "PyQt5" to "PyQt5",
                                "pyspark" to "pyspark") // top-level package to package on PyPI, sorted by the latter

    private val BALLOON_SHOWING = Key.create<Boolean>("showingStubPackagesAdvertiserBalloon")
    private val BALLOON_NOTIFICATIONS = NotificationGroup("Python Stub Packages Advertiser", NotificationDisplayType.STICKY_BALLOON, false)

    private val SESSION_KEY = Key.create<MutableSet<String>>("PyStubPackagesAdvertiser.Sources")
  }

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoredPackages: MutableList<String> = mutableListOf()

  override fun createOptionsPanel(): JComponent = ListEditForm("Ignored packages", ignoredPackages).contentPanel

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    val sources = session.putUserDataIfAbsent(SESSION_KEY, mutableSetOf())
    return Visitor(sources, holder, session)
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
    val sources = session.getUserData(SESSION_KEY)
    if (sources.isNullOrEmpty()) return

    val file = session.file
    if (file is PyFile) run(file, sources!!, problemsHolder)
  }

  private class Visitor(val sources: MutableSet<String>,
                        holder: ProblemsHolder,
                        session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyFromImportStatement(node: PyFromImportStatement) {
      super.visitPyFromImportStatement(node)
      processImport(node.importSource, node.importSourceQName)
    }

    override fun visitPyImportElement(node: PyImportElement) {
      super.visitPyImportElement(node)
      processImport(node.importReferenceExpression, node.importedQName)
    }

    private fun processImport(ref: PyReferenceExpression?, qName: QualifiedName?) {
      if (qName == null) return

      if (ref != null &&
          ref.getReference(resolveContext).multiResolve(false).asSequence().mapNotNull { it.element }.any { isInStubPackage(it) }) {
        return
      }

      qName.firstComponent?.let(sources::add)
    }
  }

  private fun run(file: PyFile,
                  sources: Set<String>,
                  problemsHolder: ProblemsHolder) {
    val module = ModuleUtilCore.findModuleForFile(file) ?: return
    val sdk = PythonSdkType.findPythonSdk(module) ?: return

    val installedPackages = PyPackageManager.getInstance(sdk).packages ?: emptyList()
    if (installedPackages.isEmpty()) return

    val availablePackages = PyPackageManagers.getInstance().getManagementService(file.project, sdk).allPackagesCached
    if (availablePackages.isEmpty()) return

    val cache = ServiceManager.getService(PyStubPackagesAdvertiserCache::class.java).forSdk(sdk)

    processForcedPackages(file, sources, module, sdk, availablePackages, installedPackages, cache, problemsHolder)
    processCheckedPackages(file, sources, module, sdk, availablePackages, installedPackages, cache)
  }

  private fun processForcedPackages(file: PyFile,
                                    sources: Set<String>,
                                    module: Module,
                                    sdk: Sdk,
                                    availablePackages: List<RepoPackage>,
                                    installedPackages: List<PyPackage>,
                                    cache: Cache<String, Set<RepoPackage>>,
                                    problemsHolder: ProblemsHolder) {
    val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(forcedSourcesToProcess(sources), cache)

    val sourceToStubPkgsAvailableToInstall = sourceToStubPackagesAvailableToInstall(
      sourceToInstalledRuntimeAndStubPackages(sourcesToLoad, FORCED, installedPackages),
      availablePackages
    )

    sourceToStubPkgsAvailableToInstall.forEach { source, stubPkgs -> cache.put(source, stubPkgs) }

    val (reqs, args) = toRequirementsAndExtraArgs(sourceToStubPkgsAvailableToInstall, cached)
    if (reqs.isNotEmpty()) {
      val plural = reqs.size > 1
      val reqsToString = PyPackageUtil.requirementsToString(reqs)

      problemsHolder.registerProblem(file,
                                     "Stub package${if (plural) "s" else ""} $reqsToString ${if (plural) "are" else "is"} not installed. " +
                                     "${if (plural) "They" else "It"} contain${if (plural) "" else "s"} type hints needed for better code insight.",
                                     createInstallStubPackagesQuickFix(reqs, args, module, sdk),
                                     createIgnorePackagesQuickFix(reqs, ignoredPackages))
    }
  }

  private fun processCheckedPackages(file: PyFile,
                                     sources: Set<String>,
                                     module: Module,
                                     sdk: Sdk,
                                     availablePackages: List<RepoPackage>,
                                     installedPackages: List<PyPackage>,
                                     cache: Cache<String, Set<RepoPackage>>) {
    val project = file.project
    if (project.getUserData(BALLOON_SHOWING) == true) return

    val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(checkedSourcesToProcess(sources), cache)

    val sourceToStubPkgsAvailableToInstall = sourceToStubPackagesAvailableToInstall(
      sourceToInstalledRuntimeAndStubPackages(sourcesToLoad, CHECKED, installedPackages),
      availablePackages
    )

    sourceToStubPkgsAvailableToInstall.forEach { source, stubPkgs -> cache.put(source, stubPkgs) }

    val (reqs, args) = toRequirementsAndExtraArgs(sourceToStubPkgsAvailableToInstall, cached)
    if (reqs.isNotEmpty()) {
      val plural = reqs.size > 1
      val reqsToString = PyPackageUtil.requirementsToString(reqs)

      project.putUserData(BALLOON_SHOWING, true)

      BALLOON_NOTIFICATIONS
        .createNotification(
          "Type hints are not installed",
          "They are needed for better code insight.<br/>" +
          "<a href=\"#yes\">Install ${if (plural) "stub packages" else reqsToString}</a>&nbsp;&nbsp;&nbsp;&nbsp;" +
          "<a href=\"#no\">Ignore</a>&nbsp;&nbsp;&nbsp;&nbsp;" +
          "<a href=\"#settings\">Settings</a>",
          NotificationType.INFORMATION
        ) { notification, event ->
          try {
            val problemDescriptor = ProblemDescriptorImpl(
              file,
              file,
              "Stub package${if (plural) "s" else ""} $reqsToString ${if (plural) "are" else "is"} not installed",
              LocalQuickFix.EMPTY_ARRAY,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              true,
              null,
              true
            )

            when (event.description) {
              "#yes" -> {
                createInstallStubPackagesQuickFix(reqs, args, module, sdk).applyFix(project, problemDescriptor)
              }
              "#no" -> createIgnorePackagesQuickFix(reqs, ignoredPackages).applyFix(project, problemDescriptor)
              "#settings" -> {
                val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
                EditInspectionToolsSettingsAction.editToolSettings(project, profile, PyStubPackagesAdvertiser::class.simpleName)
              }
            }
          }
          finally {
            notification.expire()
          }
        }
        .whenExpired { project.putUserData(BALLOON_SHOWING, false) }
        .notify(project)
    }
  }

  private fun forcedSourcesToProcess(sources: Set<String>) = sources.filterTo(mutableSetOf()) { it in FORCED }

  private fun checkedSourcesToProcess(sources: Set<String>) = sources.filterTo(mutableSetOf()) { it in CHECKED }

  private fun splitIntoNotCachedAndCached(sources: Set<String>,
                                          cache: Cache<String, Set<RepoPackage>>): Pair<Set<String>, Set<RepoPackage>> {
    if (sources.isEmpty()) return emptySet<String>() to emptySet()

    val notCached = mutableSetOf<String>()
    val cached = mutableSetOf<RepoPackage>()

    synchronized(cache) {
      // despite cache is thread-safe,
      // here we have sync block to guarantee only one reader
      // and as a result not run processing for sources that are already evaluating

      sources.forEach { source ->
        cache.getIfPresent(source).let {
          if (it == null) {
            notCached.add(source)

            // mark this source as evaluating
            // if source processing failed, this value would mean that such source was handled
            cache.put(source, emptySet())
          }
          else {
            cached.addAll(it)
          }
        }
      }
    }

    return notCached to cached
  }

  private fun sourceToInstalledRuntimeAndStubPackages(sourcesToLoad: Set<String>,
                                                      sourceToPackage: Map<String, String>,
                                                      installedPackages: List<PyPackage>): Map<String, List<Pair<PyPackage, PyPackage?>>> {
    val result = mutableMapOf<String, List<Pair<PyPackage, PyPackage?>>>()

    for (source in sourcesToLoad) {
      val pkgName = sourceToPackage[source] ?: continue
      if (ignoredPackages.contains(pkgName)) continue

      installedRuntimeAndStubPackages(pkgName, installedPackages)?.let { result.put(source, listOf(it)) }
    }

    return result
  }

  private fun sourceToStubPackagesAvailableToInstall(sourceToInstalledRuntimeAndStubPkgs: Map<String, List<Pair<PyPackage, PyPackage?>>>,
                                                     availablePackages: List<RepoPackage>): Map<String, Set<RepoPackage>> {
    if (sourceToInstalledRuntimeAndStubPkgs.isEmpty()) return emptyMap()

    val stubPkgsAvailableToInstall = mutableMapOf<String, RepoPackage>()
    availablePackages.forEach { if (it.name.endsWith(STUBS_SUFFIX)) stubPkgsAvailableToInstall[it.name] = it }

    val result = mutableMapOf<String, Set<RepoPackage>>()
    sourceToInstalledRuntimeAndStubPkgs.forEach { source, runtimeAndStubPkgs ->
      result[source] = runtimeAndStubPkgs
        .asSequence()
        .filter { it.second == null }
        .mapNotNull { stubPkgsAvailableToInstall["${it.first.name}$STUBS_SUFFIX"] }
        .toSet()
    }

    return result
  }

  private fun createInstallStubPackagesQuickFix(reqs: List<PyRequirement>, args: List<String>, module: Module, sdk: Sdk): LocalQuickFix {
    val project = module.project
    val stubPkgNamesToInstall = reqs.mapTo(mutableSetOf()) { it.name }

    val installationListener = object : PyPackageManagerUI.Listener {
      override fun started() {
        ServiceManager.getService(project, PyStubPackagesInstallingStatus::class.java).markAsInstalling(stubPkgNamesToInstall)
      }

      override fun finished(exceptions: MutableList<ExecutionException>?) {
        val status = ServiceManager.getService(project, PyStubPackagesInstallingStatus::class.java)

        val stubPkgsToUninstall = PyStubPackagesCompatibilityInspection
          .findIncompatibleRuntimeToStubPackages(sdk) { it.name in stubPkgNamesToInstall }
          .map { it.second }

        if (stubPkgsToUninstall.isNotEmpty()) {
          val stubPkgNamesToUninstall = stubPkgsToUninstall.mapTo(mutableSetOf()) { it.name }

          val uninstallationListener = object : PyPackageManagerUI.Listener {
            override fun started() {}

            override fun finished(exceptions: MutableList<ExecutionException>?) {
              status.unmarkAsInstalling(stubPkgNamesToUninstall)
            }
          }

          val plural = stubPkgNamesToUninstall.size > 1
          val content = "Suggested ${stubPkgNamesToUninstall.joinToString { "'$it'" }} " +
                        "${if (plural) "are" else "is"} incompatible with your current environment.<br/>" +
                        "${if (plural) "These" else "This"} stub package${if (plural) "s" else ""} will be removed."

          BALLOON_NOTIFICATIONS.createNotification(content, NotificationType.WARNING).notify(project)
          PyPackageManagerUI(project, sdk, uninstallationListener).uninstall(stubPkgsToUninstall)

          stubPkgNamesToInstall.removeAll(stubPkgNamesToUninstall)
        }

        status.unmarkAsInstalling(stubPkgNamesToInstall)
      }
    }

    val name = "Install stub package" + if (reqs.size > 1) "s" else ""
    return PyInstallRequirementsFix(name, module, sdk, reqs, args, installationListener)
  }

  private fun createIgnorePackagesQuickFix(reqs: List<PyRequirement>, ignoredPkgs: MutableList<String>): LocalQuickFix {
    return object : LocalQuickFix {
      override fun getFamilyName() = "Ignore package" + if (reqs.size > 1) "s" else ""

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val pkgNames = reqs.asSequence().map { it.name.removeSuffix(STUBS_SUFFIX) }
        if (ignoredPkgs.addAll(pkgNames)) ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
      }
    }
  }

  private fun toRequirementsAndExtraArgs(loaded: Map<String, Set<RepoPackage>>,
                                         cached: Set<RepoPackage>): Pair<List<PyRequirement>, List<String>> {
    val reqs = mutableListOf<PyRequirement>()
    val args = mutableListOf("--no-deps")

    (cached.asSequence().filterNot { ignoredPackages.contains(it.name.removeSuffix(STUBS_SUFFIX)) } + loaded.values.asSequence().flatten())
      .forEach {
        val version = it.latestVersion
        val url = it.repoUrl

        reqs.add(if (version == null) pyRequirement(it.name) else pyRequirement(it.name, PyRequirementRelation.EQ, version))

        if (url != null && !PyPIPackageUtil.isPyPIRepository(url)) {
          with(args) {
            add("--extra-index-url")
            add(url)
          }
        }
      }

    return reqs to args
  }

  private fun installedRuntimeAndStubPackages(pkgName: String,
                                              installedPackages: List<PyPackage>): Pair<PyPackage, PyPackage?>? {
    var runtime: PyPackage? = null
    var stub: PyPackage? = null
    val stubPkgName = "$pkgName$STUBS_SUFFIX"

    for (pkg in installedPackages) {
      val name = pkg.name

      if (name == pkgName) runtime = pkg
      if (name == stubPkgName) stub = pkg
    }

    return if (runtime == null) null else runtime to stub
  }
}