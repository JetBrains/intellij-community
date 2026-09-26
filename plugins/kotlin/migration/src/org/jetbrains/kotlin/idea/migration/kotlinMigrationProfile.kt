// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.migration

import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.createSimple
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import org.jdom.Element
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix

fun createMigrationProfile(
    managerEx: InspectionManagerEx,
    psiElement: PsiElement?,
    migrationInfo: MigrationInfo? = null
): InspectionProfileImpl {
    val rootProfile = InspectionProfileManager.getInstance().currentProfile

    val migrationFixWrappers = applicableMigrationToolsImpl(migrationInfo)

    val allWrappers = LinkedHashSet<InspectionToolWrapper<*, *>>()
    for (toolWrapper in migrationFixWrappers) {
        allWrappers.add(toolWrapper)
        rootProfile.collectDependentInspections(toolWrapper, allWrappers, managerEx.project)
    }

    val model = createSimple(KotlinMigrationBundle.message("inspection.migration.profile.name"), managerEx.project, migrationFixWrappers)
    try {
        val element = Element("toCopy")
        for (wrapper in migrationFixWrappers) {
            wrapper.tool.writeSettings(element)
            val tw = (if (psiElement == null)
                model.getInspectionTool(wrapper.shortName, managerEx.project)
            else
                model.getInspectionTool(wrapper.shortName, psiElement))!!

            tw.tool.readSettings(element)
        }
    } catch (ignored: WriteExternalException) {
    } catch (ignored: InvalidDataException) {
    }

    return model
}

fun applicableMigrationToolExists(migrationInfo: MigrationInfo): Boolean = applicableMigrationToolsImpl(migrationInfo).isNotEmpty()

private fun applicableMigrationToolsImpl(migrationInfo: MigrationInfo?): List<InspectionToolWrapper<*, *>> {
    val rootProfile = InspectionProfileManager.getInstance().currentProfile

    return rootProfile.allTools.asSequence()
        .map { it.tool }
        .filter { toolWrapper: InspectionToolWrapper<*, *> ->
            val tool = toolWrapper.tool
            tool is MigrationFix && (migrationInfo == null || tool.isApplicable(migrationInfo))
        }
        .toList()
}