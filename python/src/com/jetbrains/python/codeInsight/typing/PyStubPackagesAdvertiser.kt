// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

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
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.PackageManagementService
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.PyInstallRequirementsFix
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.sdk.PythonSdkType
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent

class PyStubPackagesAdvertiser : PyInspection() {

  companion object {
    private val WHITE_LIST = mapOf("django" to "Django", "numpy" to "numpy") // top-level package to package on PyPI

    private val BALLOON_SHOWING = Key.create<Boolean>("showingStubPackagesAdvertiserBalloon")
    private val BALLOON_NOTIFICATIONS = NotificationGroup("Python Stub Packages Advertiser", NotificationDisplayType.STICKY_BALLOON, false)
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

      processWhiteListedPackages(node, module, sdk, service)
      processNotWhiteListedPackages(node, module, sdk, service)
    }

    private fun processWhiteListedPackages(file: PyFile, module: Module, sdk: Sdk, service: PackageManagementService) {
      createSuitableRequirements(
        runtimePackageToStubPackageAvailableToInstall(
          whiteListedInstalledRuntimePackageToStubPackage(file, service), service
        ),
        sdk,
        service
      )
      { reqs ->
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

    private fun processNotWhiteListedPackages(file: PyFile, module: Module, sdk: Sdk, service: PackageManagementService) {
      val project = file.project
      if (project.getUserData(BALLOON_SHOWING) == true) return

      createSuitableRequirements(
        runtimePackageToStubPackageAvailableToInstall(
          notWhiteListedInstalledRuntimePackageToStubPackage(file, service), service
        ),
        sdk,
        service
      )
      { reqs ->
        if (reqs.isNotEmpty() && file.isValid) {
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

    private fun whiteListedInstalledRuntimePackageToStubPackage(file: PyFile,
                                                                service: PackageManagementService): List<Pair<InstalledPackage, InstalledPackage?>> {
      val result = mutableListOf<Pair<InstalledPackage, InstalledPackage?>>()

      for ((source, pkgName) in WHITE_LIST) {
        if (ignoredPackages.contains(pkgName) || !PyPsiUtils.containsImport(file, source)) continue
        installedRuntimeAndStubPackages(pkgName, service)?.let { result.add(it) }
      }

      return result
    }

    private fun notWhiteListedInstalledRuntimePackageToStubPackage(file: PyFile,
                                                                   service: PackageManagementService): List<Pair<InstalledPackage, InstalledPackage?>> {
      return (file.fromImports.asSequence().mapNotNull { it.importSourceQName } + file.importTargets.asSequence().mapNotNull { it.importedQName })
        .mapNotNull { it.firstComponent } // to top-level
        .distinct()
        .filterNot { WHITE_LIST.containsKey(it) }
        .map { PyPIPackageUtil.PACKAGES_TOPLEVEL[it] ?: listOf(it) } // to corresponding package name(s)
        .flatten()
        .distinct()
        .filterNot { ignoredPackages.contains(it) }
        .mapNotNull { installedRuntimeAndStubPackages(it, service) }
        .toList()
    }

    private fun runtimePackageToStubPackageAvailableToInstall(installedRuntimeAndStubPkgs: List<Pair<InstalledPackage, InstalledPackage?>>,
                                                              service: PackageManagementService): List<Pair<InstalledPackage, String>> {
      val stubPkgNameToInstalledRuntimePkg = installedRuntimeAndStubPkgs
        .asSequence()
        .filter { it.second == null }
        .map { it.first }
        .groupBy { "${it.name}$STUBS_SUFFIX" }

      return service.allPackagesCached
        .asSequence()
        // TODO uncomment after testing
        // .filter { PyPIPackageUtil.isPyPIRepository(it.repoUrl) } // remove when PY-22079 would be fixed
        .mapNotNull { it.name }
        .mapNotNull { pkgName -> stubPkgNameToInstalledRuntimePkg[pkgName]?.let { it.first() to pkgName } }
        .toList()
    }

    private fun createSuitableRequirements(runtimePkgToStubPkgAvailableToInstall: List<Pair<InstalledPackage, String>>,
                                           sdk: Sdk,
                                           service: PackageManagementService,
                                           consumer: (List<PyRequirement>) -> Unit) {
      val count = AtomicInteger(runtimePkgToStubPkgAvailableToInstall.size)
      val result = CopyOnWriteArrayList<PyRequirement>()

      runtimePkgToStubPkgAvailableToInstall.forEach { runtimePkgAndStubPkgName ->
        val stubPkgName = runtimePkgAndStubPkgName.second

        service.fetchPackageVersions(
          stubPkgName,
          object : CatchingConsumer<List<String>, Exception> {
            override fun consume(e: Exception?) {
              if (count.decrementAndGet() == 0) consumer(result)
            }

            override fun consume(t: List<String>?) {
              if (t != null) {
                val packageManager = PyPackageManager.getInstance(sdk)

                selectStubPackageVersionToInstall(runtimePkgAndStubPkgName.first, t)
                  ?.let { packageManager.parseRequirement(stubPkgName + PyRequirementRelation.EQ.presentableText + it) }
                  ?.let { result.add(it) }
              }

              if (count.decrementAndGet() == 0) consumer(result)
            }
          }
        )
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
                                                service: PackageManagementService): Pair<InstalledPackage, InstalledPackage?>? {
      var runtime: InstalledPackage? = null
      var stub: InstalledPackage? = null
      val stubPkgName = "$pkgName$STUBS_SUFFIX"

      for (pkg in service.installedPackages) {
        val name = pkg.name

        if (name == pkgName) runtime = pkg
        if (name == stubPkgName) stub = pkg
      }

      return if (runtime == null) null else runtime to stub
    }

    private fun selectStubPackageVersionToInstall(runtimePkg: InstalledPackage, stubPkgVersions: List<String>): String? {
      val sorted = TreeSet(PyPackageVersionComparator.STR_COMPARATOR)
      sorted.addAll(stubPkgVersions)

      val runtimePkgVersion = runtimePkg.version
      return (sorted.ceiling(runtimePkgVersion) ?: sorted.lower(runtimePkgVersion))
    }
  }
}