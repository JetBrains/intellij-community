// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.utils.PathUtil

class GenerateMavenCompileExecutionAction :
    PomFileActionBase(KotlinMavenExecutionProvider(PomFile.KotlinGoals.Compile, PomFile.DefaultPhases.Compile))

class GenerateMavenTestCompileExecutionAction :
    PomFileActionBase(KotlinMavenExecutionProvider(PomFile.KotlinGoals.TestCompile, PomFile.DefaultPhases.TestCompile))

class GenerateMavenPluginAction : PomFileActionBase(KotlinMavenPluginProvider())

private const val DefaultKotlinVersion = $$"${kotlin.version}"

open class PomFileActionBase(generateProvider: AbstractDomGenerateProvider<*>) : GenerateDomElementAction(generateProvider) {
    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        return MavenDomUtil.isMavenFile(file) && super.isValidForFile(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = true
}

private class KotlinMavenPluginProvider :
    AbstractDomGenerateProvider<MavenDomPlugin>("kotlin-maven-plugin-provider", "kotlin-maven-plugin-provider", MavenDomPlugin::class.java) {

    override fun generate(parent: DomElement?, editor: Editor?): MavenDomPlugin? {
        if (parent !is MavenDomProjectModel) {
            return null
        }

        val knownVersion = parent.dependencies.dependencies.firstOrNull { it.isKotlinStdlib() }?.version?.rawText
        val version = when {
            knownVersion == null -> DefaultKotlinVersion
            knownVersion.isRangeVersion() -> knownVersion.getRangeClosedEnd() ?: DefaultKotlinVersion
            else -> knownVersion
        }

        val pom = PomFile.forFileOrNull(DomUtil.getFile(parent)) ?: return null
        return pom.addPlugin(kotlinPluginId(version))
    }

    override fun getElementToNavigate(t: MavenDomPlugin?) = t?.version

    override fun getParentDomElement(project: Project?, editor: Editor?, file: PsiFile?): DomElement? {
        if (project == null || editor == null || file == null) {
            return null
        }

        return DomUtil.getContextElement(editor)?.findProject()
    }

    override fun isAvailableForElement(contextElement: DomElement): Boolean {
        val parent = contextElement.findProject() ?: return false

        return parent.build.plugins.plugins.none(MavenDomPlugin::isKotlinMavenPlugin)
    }
}

private class KotlinMavenExecutionProvider(val goal: String, val phase: String) :
    AbstractDomGenerateProvider<MavenDomPlugin>("kotlin-maven-execution-provider", "kotlin-maven-execution-provider", MavenDomPlugin::class.java) {

    override fun generate(parent: DomElement?, editor: Editor?): MavenDomPlugin? {
        if (parent !is MavenDomPlugin) {
            return null
        }

        val file = PomFile.forFileOrNull(DomUtil.getFile(parent)) ?: return null
        val execution = file.addExecution(parent, goal, phase, listOf(goal))

        editor?.caretModel?.moveToOffset(execution.ensureXmlElementExists().endOffset)

        return parent
    }

    override fun getElementToNavigate(t: MavenDomPlugin?): DomElement? = null

    override fun getParentDomElement(project: Project?, editor: Editor?, file: PsiFile?): DomElement? {
        if (project == null || editor == null || file == null) {
            return null
        }

        return DomUtil.getContextElement(editor)?.findPlugin()
    }

    override fun isAvailableForElement(contextElement: DomElement): Boolean {
        val plugin = contextElement.findPlugin()
        return plugin != null
                && plugin.isKotlinMavenPlugin()
                && plugin.executions.executions.none { it.goals.goals.any { it.value == goal } }
    }

}

private fun String.getRangeClosedEnd(): String? = when {
    startsWith("[") -> substringBefore(',', "").drop(1).trimEnd()
    endsWith("]") -> substringAfterLast(',', "").dropLast(1).trimStart()
    else -> null
}

private fun Char.isRangeStart() = this == '[' || this == '('
private fun Char.isRangeEnd() = this == ']' || this == ')'

private fun String.isRangeVersion() = length > 2 && this[0].isRangeStart() && last().isRangeEnd()

private fun DomElement.findProject(): MavenDomProjectModel? =
    this as? MavenDomProjectModel ?: DomUtil.getParentOfType(this, MavenDomProjectModel::class.java, true)

private fun DomElement.findPlugin(): MavenDomPlugin? =
    this as? MavenDomPlugin ?: DomUtil.getParentOfType(this, MavenDomPlugin::class.java, true)

private fun MavenDomPlugin.isKotlinMavenPlugin() = groupId.stringValue == KotlinMavenConfigurator.GROUP_ID
        && artifactId.stringValue == KotlinMavenConfigurator.MAVEN_PLUGIN_ID

private fun MavenDomDependency.isKotlinStdlib(): Boolean {
    return groupId.stringValue == KotlinMavenConfigurator.GROUP_ID
            && artifactId.stringValue == PathUtil.KOTLIN_JAVA_STDLIB_NAME
}
