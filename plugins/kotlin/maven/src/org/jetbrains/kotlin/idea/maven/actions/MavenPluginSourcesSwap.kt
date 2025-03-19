// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.actions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomBuild
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class MavenPluginSourcesMoveToExecutionIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = KotlinMavenBundle.message("fix.move.to.execution.family")
    override fun getText(): String = familyName

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val file = element.containingFile

        if (file == null || !MavenDomUtil.isMavenFile(file) || element !is XmlElement) {
            return false
        }

        val tag = element.getParentOfType<XmlTag>(false) ?: return false
        val domElement = DomManager.getDomManager(project).getDomElement(tag) ?: return false

        if (domElement !is GenericDomValue<*>) {
            return false
        }

        if (MavenDomUtil.getMavenDomProjectModel(project, file.virtualFile) == null) {
            return false
        }

        val pom = PomFile.forFileOrNull(file as XmlFile) ?: return false
        if (domElement.getParentOfType(MavenDomBuild::class.java, false)?.sourceDirectory === domElement) {
            return pom.findKotlinExecutions(PomFile.KotlinGoals.Compile, PomFile.KotlinGoals.Js).isNotEmpty()
        }
        if (domElement.getParentOfType(MavenDomBuild::class.java, false)?.testSourceDirectory === domElement) {
            return pom.findKotlinExecutions(PomFile.KotlinGoals.TestCompile, PomFile.KotlinGoals.TestJs).isNotEmpty()
        }

        return false
    }

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val xmlFile = element.containingFile as? XmlFile ?: return
        val pomFile = PomFile.forFileOrNull(xmlFile) ?: return

        val tag = element.getParentOfType<XmlTag>(false) ?: return
        val domElement = DomManager.getDomManager(project).getDomElement(tag) as? GenericDomValue<*> ?: return
        val dir = domElement.rawText ?: return

        val relevantExecutions = when {
            domElement.getParentOfType(MavenDomBuild::class.java, false)?.sourceDirectory === domElement ->
                pomFile.findKotlinExecutions(PomFile.KotlinGoals.Compile, PomFile.KotlinGoals.Js)
            domElement.getParentOfType(MavenDomBuild::class.java, false)?.testSourceDirectory === domElement ->
                pomFile.findKotlinExecutions(PomFile.KotlinGoals.TestCompile, PomFile.KotlinGoals.TestJs)
            else -> emptyList()
        }

        if (relevantExecutions.isNotEmpty()) {
            relevantExecutions.forEach { execution ->
                val existingSourceDirs = pomFile.executionSourceDirs(execution)
                pomFile.executionSourceDirs(execution, (existingSourceDirs + dir).distinct(), true)
            }

            domElement.undefine()
        }
    }
}

class MavenPluginSourcesMoveToBuild : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = KotlinMavenBundle.message("fix.move.to.build.family")
    override fun getText(): String = familyName

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return tryInvoke(project, element)
    }

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        tryInvoke(project, element) { pom, dir, execution, _ ->
            pom.executionSourceDirs(execution, listOf(dir))
        }
    }

    private fun tryInvoke(
        project: Project,
        element: PsiElement,
        block: (pom: PomFile, dir: String, execution: MavenDomPluginExecution, build: MavenDomBuild) -> Unit = { _, _, _, _ -> }
    ): Boolean {
        val file = element.containingFile

        if (file == null || !MavenDomUtil.isMavenFile(file) || (element !is XmlElement && element.parent !is XmlElement)) {
            return false
        }

        val tag = element.getParentOfType<XmlTag>(false) ?: return false
        val domElement = DomManager.getDomManager(project).getDomElement(tag) ?: return false

        val execution = domElement.getParentOfType(MavenDomPluginExecution::class.java, false) ?: return false
        tag.parentsWithSelf
            .takeWhile { it != execution.xmlElement }
            .filterIsInstance<XmlTag>()
            .firstOrNull { it.localName == "sourceDirs" } ?: return false

        val pom = PomFile.forFileOrNull(element.containingFile as XmlFile) ?: return false
        val sourceDirsToMove = pom.executionSourceDirs(execution)

        if (sourceDirsToMove.size != 1) {
            return false
        }

        val build = execution.getParentOfType(MavenDomBuild::class.java, false) ?: return false
        var couldMove = 0
        if (shouldMoveCompileSourceRoot(execution)) {
            if (!build.sourceDirectory.exists() || build.sourceDirectory.stringValue == sourceDirsToMove.single()) {
                couldMove++
            }
        }
        if (shouldMoveTestSourceRoot(execution)) {
            if (!build.testSourceDirectory.exists() || build.testSourceDirectory.stringValue == sourceDirsToMove.single()) {
                couldMove++
            }
        }

        return if (couldMove == 1) {
            block(pom, sourceDirsToMove.single(), execution, build)
            true
        } else {
            false
        }
    }

    private fun shouldMoveCompileSourceRoot(execution: MavenDomPluginExecution) =
        execution.goals.goals.any { it.stringValue == PomFile.KotlinGoals.Compile || it.stringValue == PomFile.KotlinGoals.Js }

    private fun shouldMoveTestSourceRoot(execution: MavenDomPluginExecution) =
        execution.goals.goals.any { it.stringValue == PomFile.KotlinGoals.TestCompile || it.stringValue == PomFile.KotlinGoals.TestJs }
}
