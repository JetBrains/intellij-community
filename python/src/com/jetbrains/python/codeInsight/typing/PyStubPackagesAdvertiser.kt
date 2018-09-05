// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.google.common.cache.CacheBuilder
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.PackageManagementService
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.PyInstallRequirementsFix
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.sdk.PythonSdkType
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent

class PyStubPackagesAdvertiser : PyInspection() {

  companion object {
    private val WHITE_LIST = mapOf("django" to "Django", "numpy" to "numpy") // top-level package to package on PyPI

    private val BALLOON_SHOWING = Key.create<Boolean>("showingStubPackagesAdvertiserBalloon")
    private val BALLOON_NOTIFICATIONS = NotificationGroup("Python Stub Packages Advertiser", NotificationDisplayType.STICKY_BALLOON, false)

    private val CACHE = CacheBuilder.newBuilder()
      .maximumSize(200)
      .expireAfterAccess(Duration.ofMinutes(5))
      .build<Pair<Sdk, String>, Set<PyRequirement>>()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoredPackages: MutableList<String> = mutableListOf()

  override fun createOptionsPanel(): JComponent = ListEditForm("Ignored packages", ignoredPackages).contentPanel

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(ignoredPackages, holder, session)
  }

  private class Visitor(val ignoredPackages: MutableList<String>,
                        holder: ProblemsHolder,
                        session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyFile(node: PyFile) {
      val module = ModuleUtilCore.findModuleForFile(node) ?: return
      val sdk = PythonSdkType.findPythonSdk(module) ?: return
      val service = PyPackageManagers.getInstance().getManagementService(node.project, sdk)
      val installedPackages = PyPackageManager.getInstance(sdk).packages ?: emptyList()
      if (installedPackages.isEmpty()) return

      processWhiteListedPackages(node, module, sdk, service, installedPackages)
      processNotWhiteListedPackages(node, module, sdk, service, installedPackages)
    }

    private fun processWhiteListedPackages(file: PyFile,
                                           module: Module,
                                           sdk: Sdk,
                                           service: PackageManagementService,
                                           installedPackages: List<PyPackage>) {
      val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(whiteListedSourcesToProcess(file), sdk)

      createSuitableRequirements(
        sourceToInstalledRuntimeAndStubPackagesAvailableToInstall(
          whiteListedSourceToInstalledRuntimeAndStubPackages(sourcesToLoad, installedPackages),
          service
        ),
        sdk,
        service
      )
      { loadedReqs ->
        if (!file.isValid) return@createSuitableRequirements

        val reqs = loadedReqs + cached
        if (reqs.isNotEmpty() && file.isValid) {
          val plural = reqs.size > 1
          val reqsToString = PyPackageUtil.requirementsToString(reqs)

          val installQuickFix = PyInstallRequirementsFix("Install stub package" + if (plural) "s" else "", module, sdk, reqs)
          val ignoreQuickFix = createIgnorePackagesQuickFix(reqs, ignoredPackages)

          registerProblem(file,
                          "Stub package${if (plural) "s" else ""} $reqsToString ${if (plural) "are" else "is"} not installed. " +
                          "${if (plural) "They" else "It"} contain${if (plural) "" else "s"} type hints needed for better code insight.",
                          installQuickFix,
                          ignoreQuickFix)
        }
      }
    }

    private fun processNotWhiteListedPackages(file: PyFile,
                                              module: Module,
                                              sdk: Sdk,
                                              service: PackageManagementService,
                                              installedPackages: List<PyPackage>) {
      val project = file.project
      if (project.getUserData(BALLOON_SHOWING) == true) return

      val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(notWhiteListedSourcesToProcess(file), sdk)

      createSuitableRequirements(
        sourceToInstalledRuntimeAndStubPackagesAvailableToInstall(
          notWhiteListedSourceToInstalledRuntimeAndStubPackages(sourcesToLoad, installedPackages),
          service
        ),
        sdk,
        service
      )
      { loadedReqs ->
        if (!file.isValid) return@createSuitableRequirements

        val reqs = loadedReqs + cached
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
                    val installQuickFix = PyInstallRequirementsFix("Install stub package" + if (plural) "s" else "", module, sdk, reqs)
                    installQuickFix.applyFix(project, problemDescriptor)
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
                project.putUserData(BALLOON_SHOWING, false)
              }
            }
            .notify(project)
        }
      }
    }

    private fun whiteListedSourcesToProcess(file: PyFile): Set<String> {
      return WHITE_LIST.keys.filterTo(mutableSetOf()) { PyPsiUtils.containsImport(file, it) }
    }

    private fun notWhiteListedSourcesToProcess(file: PyFile): Set<String> {
      return (file.fromImports.asSequence().mapNotNull { it.importSourceQName } + file.importTargets.asSequence().mapNotNull { it.importedQName })
        .mapNotNull { it.firstComponent } // to top-level
        .filterNot { it in WHITE_LIST }
        .toSet()
    }

    private fun splitIntoNotCachedAndCached(sources: Set<String>, sdk: Sdk): Pair<Set<String>, Set<PyRequirement>> {
      val notCached = mutableSetOf<String>()
      val cached = mutableSetOf<PyRequirement>()

      synchronized(CACHE) {
        // despite cache is thread-safe,
        // here we have sync block to guarantee only one reader
        // and as a result not run processing for sources that are already evaluating

        sources.forEach { source ->
          val key = sdk to source

          CACHE.getIfPresent(key).let {
            if (it == null) {
              notCached.add(source)

              // mark this source as evaluating
              // if source processing failed, this value would mean that such source was handled
              CACHE.put(key, emptySet())
            }
            else {
              cached.addAll(it)
            }
          }
        }
      }

      return notCached to cached
    }

    private fun whiteListedSourceToInstalledRuntimeAndStubPackages(sourcesToLoad: Set<String>,
                                                                   installedPackages: List<PyPackage>): Map<String, List<Pair<PyPackage, PyPackage?>>> {
      val result = mutableMapOf<String, List<Pair<PyPackage, PyPackage?>>>()

      for (source in sourcesToLoad) {
        val pkgName = WHITE_LIST[source] ?: continue
        if (ignoredPackages.contains(pkgName)) continue

        installedRuntimeAndStubPackages(pkgName, installedPackages)?.let { result.put(source, listOf(it)) }
      }

      return result
    }

    private fun notWhiteListedSourceToInstalledRuntimeAndStubPackages(sourcesToLoad: Set<String>,
                                                                      installedPackages: List<PyPackage>): Map<String, List<Pair<PyPackage, PyPackage?>>> {
      val result = mutableMapOf<String, List<Pair<PyPackage, PyPackage?>>>()

      for (source in sourcesToLoad) {
        val packageNames = PyPIPackageUtil.PACKAGES_TOPLEVEL[source] ?: listOf(source)

        result[source] = packageNames.mapNotNull {
          if (ignoredPackages.contains(it)) null
          else installedRuntimeAndStubPackages(it, installedPackages)
        }
      }

      return result
    }

    private fun sourceToInstalledRuntimeAndStubPackagesAvailableToInstall(sourceToInstalledRuntimeAndStubPkgs: Map<String, List<Pair<PyPackage, PyPackage?>>>,
                                                                          service: PackageManagementService): Map<String, List<Pair<PyPackage, String>>> {
      val stubPkgNamesAvailableToInstall = service.allPackagesCached
        .asSequence()
        // TODO uncomment after testing
        // .filter { PyPIPackageUtil.isPyPIRepository(it.repoUrl) } // remove when PY-22079 would be fixed
        .mapNotNull { it.name }
        .filter { it.endsWith(STUBS_SUFFIX) }
        .toSet()

      val result = mutableMapOf<String, List<Pair<PyPackage, String>>>()
      sourceToInstalledRuntimeAndStubPkgs.forEach { source, runtimeAndStubPkgs ->
        result[source] = runtimeAndStubPkgs
          .asSequence()
          .map { it.first }
          .filter { "${it.name}$STUBS_SUFFIX" in stubPkgNamesAvailableToInstall }
          .map { it to "${it.name}$STUBS_SUFFIX" }
          .toList()
      }

      return result
    }

    private fun createSuitableRequirements(sourceToInstalledRuntimeAndStubPkgsAvailableToInstall: Map<String, List<Pair<PyPackage, String>>>,
                                           sdk: Sdk,
                                           service: PackageManagementService,
                                           consumer: (List<PyRequirement>) -> Unit) {
      if (sourceToInstalledRuntimeAndStubPkgsAvailableToInstall.isEmpty()) {
        consumer(emptyList())
        return
      }

      val count = AtomicInteger(sourceToInstalledRuntimeAndStubPkgsAvailableToInstall.size)
      val forConsumer = CopyOnWriteArrayList<PyRequirement>()
      val toCache = mutableMapOf<String, MutableSet<PyRequirement>>()

      sourceToInstalledRuntimeAndStubPkgsAvailableToInstall.keys.forEach { toCache[it] = ConcurrentHashMap.newKeySet() }

      sourceToInstalledRuntimeAndStubPkgsAvailableToInstall.forEach { entry ->
        val source = entry.key

        entry.value.forEach { installedRuntimeAndStubPkgAvailableToInstall ->
          val stubPkgName = installedRuntimeAndStubPkgAvailableToInstall.second

          service.fetchPackageVersions(
            stubPkgName,
            object : CatchingConsumer<List<String>, Exception> {
              override fun consume(e: Exception?) {
                if (count.decrementAndGet() == 0) {
                  toCache.forEach { source, reqs -> CACHE.put(sdk to source, reqs) }
                  consumer(forConsumer)
                }
              }

              override fun consume(t: List<String>?) {
                if (t != null) {
                  val packageManager = PyPackageManager.getInstance(sdk)

                  selectStubPackageVersionToInstall(installedRuntimeAndStubPkgAvailableToInstall.first, t)
                    ?.let { packageManager.parseRequirement(stubPkgName + PyRequirementRelation.EQ.presentableText + it) }
                    ?.let {
                      toCache[source]?.add(it)
                      forConsumer.add(it)
                    }
                }

                if (count.decrementAndGet() == 0) {
                  toCache.forEach { source, reqs -> CACHE.put(sdk to source, reqs) }
                  consumer(forConsumer)
                }
              }
            }
          )
        }
      }
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

    private fun selectStubPackageVersionToInstall(runtimePkg: PyPackage, stubPkgVersions: List<String>): String? {
      val sorted = TreeSet(PyPackageVersionComparator.STR_COMPARATOR)
      sorted.addAll(stubPkgVersions)

      val runtimePkgVersion = runtimePkg.version
      return (sorted.ceiling(runtimePkgVersion) ?: sorted.lower(runtimePkgVersion))
    }
  }
}