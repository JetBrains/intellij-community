package com.jetbrains.packagesearch.intellij.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService

class AddDependencyAction : AnAction(
    PackageSearchBundle.message("packagesearch.actions.addDependency.text"),
    PackageSearchBundle.message("packagesearch.actions.addDependency.description"),
    PackageSearchIcons.Artifact
) {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = run {
            val project = e.project ?: return@run false

            val dataContext = e.dataContext
            val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return@run false

            val psiFile: PsiFile? = PsiUtilBase.getPsiFileInEditor(editor, project)
            if (psiFile == null || ProjectModuleOperationProvider.forProjectPsiFileOrNull(project, psiFile) == null) {
                return@run false
            }

            val rootModel = project.packageSearchDataService
            val modules = rootModel.dataModelFlow.value.moduleModels
            findSelectedModule(e, modules) != null
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val rootModel = project.packageSearchDataService
        val modules = rootModel.dataModelFlow.value.moduleModels
        if (modules.isEmpty()) return

        val selectedModule = findSelectedModule(e, modules) ?: return

        PackageSearchToolWindowFactory.activateToolWindow(project) {
            rootModel.setTargetModules(TargetModules.One(selectedModule))
        }
    }

    private fun findSelectedModule(e: AnActionEvent, modules: List<ModuleModel>): ModuleModel? {
        val project = e.project ?: return null
        val file = obtainSelectedProjectDirIfSingle(e)?.virtualFile ?: return null
        val selectedModule = runReadAction { ModuleUtilCore.findModuleForFile(file, project) } ?: return null

        // Sanity check that the module we got actually exists
        ModuleManager.getInstance(project).findModuleByName(selectedModule.name)
            ?: return null

        return modules.firstOrNull { module -> module.projectModule.nativeModule == selectedModule }
    }

    private fun obtainSelectedProjectDirIfSingle(e: AnActionEvent): PsiDirectory? {
        val dataContext = e.dataContext
        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext)
        val selectedDirectories = ideView?.directories ?: return null

        if (selectedDirectories.size != 1) return null

        return selectedDirectories.first()
    }
}
