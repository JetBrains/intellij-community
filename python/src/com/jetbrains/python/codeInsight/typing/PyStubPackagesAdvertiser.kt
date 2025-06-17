// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.google.common.cache.Cache
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.execution.ExecutionException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyStubPackagesAdvertiserCache.Companion.StubPackagesForSource
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.quickfix.PyInstallRequirementsFix
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.sdk.PythonSdkUtil

private class PyStubPackagesAdvertiser : PyInspection() {
  companion object {
    // file-level suggestion will be shown for packages below
    private val FORCED = emptyMap<String, String>() // top-level package to package on PyPI

    // notification will be shown for packages below
    private val CHECKED = mapOf("docutils" to "docutils",
                                "gi" to "PyGObject",
                                "PyQt5" to "PyQt5",
                                "pandas" to "pandas",
                                "celery" to "celery",
                                "boto3" to "boto3",
                                "scipy" to "scipy",
                                "traits" to "traits") // top-level package to package on PyPI, sorted by the latter

    private val EXTRAS = mapOf("boto3-stubs" to "[full]")

    private val IGNORE = setOf(
      "types-boto3", // duplicate of boto3-stubs
      "celery-stubs", // deprecated
    )

    private val BALLOON_SHOWING = Key.create<Boolean>("showingStubPackagesAdvertiserBalloon")
  }

  var ignoredPackages: MutableList<String> = mutableListOf()

  override fun getOptionsPane() =
    pane(stringList("ignoredPackages", PyPsiBundle.message("INSP.stub.packages.compatibility.ignored.packages.label")))

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(ignoredPackages, holder, session)

  private class Visitor(private val ignoredPackages: MutableList<String>,
                        holder: ProblemsHolder,
                        session: LocalInspectionToolSession) : PyInspectionVisitor(holder, getContext(session)) {

    private val BALLOON_NOTIFICATIONS
      get() = NotificationGroupManager.getInstance().getNotificationGroup("Python Stub Packages Advertiser")

    override fun visitPyFile(node: PyFile) {
      super.visitPyFile(node)

      val sources = mutableSetOf<String>()

      node.fromImports.mapNotNullTo(sources) { topLevelPackagesWithoutStubs(it.importSource, it.importSourceQName) }
      node.importTargets.mapNotNullTo(sources) { topLevelPackagesWithoutStubs(it.importReferenceExpression, it.importedQName) }

      if (sources.isNotEmpty()) {
        run(node, sources)
      }
    }

    private fun topLevelPackagesWithoutStubs(ref: PyReferenceExpression?, qName: QualifiedName?): String? {
      if (qName == null) return null

      if (ref != null &&
          ref.getReference(resolveContext).multiResolve(false).asSequence().mapNotNull { it.element }.any { isInStubPackage(it) }) {
        return null
      }

      return qName.firstComponent
    }

    private fun run(file: PyFile, sources: Set<String>) {
      val module = ModuleUtilCore.findModuleForFile(file) ?: return
      val sdk = PythonSdkUtil.findPythonSdk(module) ?: return

      val packageManager = PythonPackageManager.forSdk(module.project, sdk)
      val installedPackages = packageManager.listInstalledPackagesSnapshot()
      if (installedPackages.isEmpty()) return

      val packageManagementService = PyPackageManagers.getInstance().getManagementService(file.project, sdk)
      val availablePackages = packageManagementService.allPackagesCached
      if (availablePackages.isEmpty()) return

      val ignoredStubPackages = (IGNORE + ignoredPackages).mapNotNull { PyRequirementParser.fromLine(it) }
      val cache = ApplicationManager.getApplication().service<PyStubPackagesAdvertiserCache>().forSdk(sdk)

      val forcedToLoad = processForcedPackages(file, sources, module, sdk, ignoredStubPackages, cache)
      val checkedToLoad = processCheckedPackages(file, sources, module, sdk, ignoredStubPackages, cache)

      loadStubPackagesForSources(
        forcedToLoad + checkedToLoad,
        FORCED + CHECKED,
        installedPackages,
        availablePackages,
        packageManagementService,
        sdk
      )
    }

    private fun processForcedPackages(
      file: PyFile,
      sources: Set<String>,
      module: Module,
      sdk: Sdk,
      ignoredStubPackages: List<PyRequirement>,
      cache: Cache<String, StubPackagesForSource>
    ): Set<String> {
      val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(forcedSourcesToProcess(sources), cache)

      val (reqs, args) = toRequirementsAndExtraArgs(cached, ignoredStubPackages)
      if (reqs.isNotEmpty()) {
        val reqsToString = PyPackageUtil.requirementsToString(reqs)
        val message = PyBundle.message("code.insight.stub.forced.packages.are.not.installed.message", reqs.size, reqsToString)

        registerProblem(file,
                        message,
                        createInstallStubPackagesQuickFix(reqs, args, module, sdk),
                        createIgnorePackagesQuickFix(reqs))
      }

      return sourcesToLoad
    }

    private fun processCheckedPackages(
      file: PyFile,
      sources: Set<String>,
      module: Module,
      sdk: Sdk,
      ignoredStubPackages: List<PyRequirement>,
      cache: Cache<String, StubPackagesForSource>
    ): Set<String> {
      val project = file.project
      if (project.getUserData(BALLOON_SHOWING) == true) return emptySet()

      val (sourcesToLoad, cached) = splitIntoNotCachedAndCached(checkedSourcesToProcess(sources), cache)

      val (unfilteredReqs, args) = toRequirementsAndExtraArgs(cached, ignoredStubPackages)

      val status = file.project.service<PyStubPackagesInstallingStatus>()

      val reqs = unfilteredReqs.filterNot { status.markedAsInstalling(it.name) }

      if (reqs.isNotEmpty()) {
        val plural = reqs.size > 1
        val reqsToString = PyPackageUtil.requirementsToString(reqs)

        project.putUserData(BALLOON_SHOWING, true)

        val descriptionTemplate = PyBundle.message("code.insight.stub.checked.packages.are.not.installed.message", reqs.size, reqsToString)
        val problemDescriptor = ProblemDescriptorImpl(
          file,
          file,
          descriptionTemplate,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          true,
          null,
          true
        )

        BALLOON_NOTIFICATIONS
          .createNotification(
            PyBundle.message("code.insight.type.hints.are.not.installed"),
            PyBundle.message("code.insight.install.type.hints.content"),
            NotificationType.INFORMATION)
          .setSuggestionType(true)
          .addAction(
            NotificationAction.createSimpleExpiring(
              if (plural) PyBundle.message("code.insight.install.type.hints.action")
              else "${PyBundle.message("python.packaging.install")} $reqsToString"
            ) { createInstallStubPackagesQuickFix(reqs, args, module, sdk).applyFix(project, problemDescriptor) }
          )
          .addAction(
            NotificationAction.createSimpleExpiring(PyBundle.message("code.insight.ignore.type.hints")) {
              createIgnorePackagesQuickFix(reqs).applyFix(project, problemDescriptor)
            }
          )
          .addAction(
            NotificationAction.createSimpleExpiring(PyBundle.message("notification.action.edit.settings")) {
              val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
              EditInspectionToolsSettingsAction.editToolSettings(project, profile, PyStubPackagesAdvertiser::class.simpleName)
            }
          )
          .setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)
          .whenExpired { project.putUserData(BALLOON_SHOWING, false) }
          .notify(project)
      }

      return sourcesToLoad
    }

    private fun forcedSourcesToProcess(sources: Set<String>) = sources.filterTo(mutableSetOf()) { it in FORCED }

    private fun checkedSourcesToProcess(sources: Set<String>) = sources.filterTo(mutableSetOf()) { it in CHECKED }

    private fun splitIntoNotCachedAndCached(sources: Set<String>,
                                            cache: Cache<String, StubPackagesForSource>): Pair<Set<String>, List<StubPackagesForSource>> {
      if (sources.isEmpty()) return emptySet<String>() to emptyList()

      val notCached = mutableSetOf<String>()
      val cached = mutableListOf<StubPackagesForSource>()

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
              cache.put(source, StubPackagesForSource.EMPTY)
            }
            else {
              cached.add(it)
            }
          }
        }
      }

      return notCached to cached
    }

    private fun toRequirementsAndExtraArgs(cached: List<StubPackagesForSource>,
                                           ignoredStubPackages: List<PyRequirement>): Pair<List<PyRequirement>, List<String>> {
      if (cached.isEmpty()) return emptyList<PyRequirement>() to emptyList()

      val requirements = cached.asSequence()
        .flatMap { it.packages.entries.asSequence() }
        .filterNot { isIgnoredStubPackage(it.key, it.value.first, ignoredStubPackages) }
        .map {
          pyRequirement(it.key, PyRequirementRelation.EQ, it.value.first, extras = EXTRAS.getOrDefault(it.key, ""))
        }
        .toList()
      if (requirements.isEmpty()) return emptyList<PyRequirement>() to emptyList()

      val args = sequenceOf("--no-deps") +
                 cached.asSequence().flatMap { pkgs -> pkgs.packages.values.asSequence().map { it.second }.flatten() }
      return requirements to args.toList()
    }

    private fun createInstallStubPackagesQuickFix(
      reqs: List<PyRequirement>,
      args: List<String>,
      module: Module,
      sdk: Sdk,
    ): LocalQuickFix {
      val project = module.project
      val stubPkgNamesToInstall = reqs.mapTo(mutableSetOf()) { it.name }

      val installationListener = object : RunningPackagingTasksListener(module) {
        override fun started() {
          project.service<PyStubPackagesInstallingStatus>().markAsInstalling(stubPkgNamesToInstall)
        }

        override fun finished(exceptions: List<ExecutionException>) {
          val status = project.service<PyStubPackagesInstallingStatus>()

          val stubPkgsToUninstall = PyStubPackagesCompatibilityInspection
            .findIncompatibleRuntimeToStubPackages(sdk) { it.name in stubPkgNamesToInstall }
            .map { it.second }

          if (stubPkgsToUninstall.isNotEmpty()) {
            val stubPkgNamesToUninstall = stubPkgsToUninstall.mapTo(mutableSetOf()) { it.name }

            val uninstallationListener = object : PyPackageManagerUI.Listener {
              override fun started() {}

              override fun finished(exceptions: MutableList<ExecutionException>?) {
                status.unmarkAsInstalling(stubPkgNamesToUninstall)
                val reqsToIgnore = stubPkgsToUninstall.map { pyRequirement(it.name, PyRequirementRelation.EQ, it.version) }
                addStubPackagesToIgnore(reqsToIgnore, stubPkgNamesToUninstall, project)
              }
            }

            val content = PyBundle.message("code.insight.stub.packages.ignored.notification.content",
                                           stubPkgNamesToUninstall.joinToString { "'$it'" }, stubPkgNamesToUninstall.size)

            BALLOON_NOTIFICATIONS.createNotification(content, NotificationType.WARNING).notify(project)
            PyPackageManagerUI(project, sdk, uninstallationListener).uninstall(stubPkgsToUninstall)

            stubPkgNamesToInstall.removeAll(stubPkgNamesToUninstall)
          }

          status.unmarkAsInstalling(stubPkgNamesToInstall)
        }
      }

      val name = PyBundle.message("code.insight.stub.packages.install.requirements.fix.name", reqs.size)
      return PyInstallRequirementsFix(name, sdk, reqs, args, installationListener)
    }

    private fun createIgnorePackagesQuickFix(reqs: List<PyRequirement>): LocalQuickFix {
      return object : LocalQuickFix {
        override fun getFamilyName() = PyBundle.message("code.insight.ignore.packages.qfix", reqs.size)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
          this@Visitor.addStubPackagesToIgnore(reqs, reqs.mapTo(mutableSetOf()) { it.name }, project)
        }
      }
    }

    private fun addStubPackagesToIgnore(stubPackages: List<PyRequirement>,
                                        stubPackagesNames: Set<String>,
                                        project: Project) {
      ignoredPackages.removeIf { PyRequirementParser.fromLine(it)?.name in stubPackagesNames }
      ignoredPackages.addAll(stubPackages.map { it.presentableText })

      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
    }

    private fun isIgnoredStubPackage(name: String, version: String, ignoredStubPackages: List<PyRequirement>): Boolean {
      val stubPackage = PyPackage(name, version)
      return ignoredStubPackages.any { stubPackage.matches(it) }
    }
  }
}
