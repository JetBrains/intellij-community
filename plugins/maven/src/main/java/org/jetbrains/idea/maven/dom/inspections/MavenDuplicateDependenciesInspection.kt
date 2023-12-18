// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.ide.nls.NlsMessages
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.Processor
import com.intellij.util.containers.MultiMap
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementsInspection
import org.jetbrains.idea.maven.dom.DependencyConflictId
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenLog

class MavenDuplicateDependenciesInspection : DomElementsInspection<MavenDomProjectModel>(MavenDomProjectModel::class.java) {
  override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel>, holder: DomElementAnnotationHolder) {
    val projectModel = domFileElement.rootElement

    checkManagedDependencies(projectModel, holder)
    checkDependencies(projectModel, holder)
  }

  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getShortName(): String {
    return "MavenDuplicateDependenciesInspection"
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.WARNING
  }

  private fun checkDependencies(projectModel: MavenDomProjectModel,
                                holder: DomElementAnnotationHolder) {
    val allDuplicates = getDuplicateDependenciesMap(projectModel)

    for (dependency in projectModel.dependencies.dependencies) {
      val id = DependencyConflictId.create(dependency)
      if (id != null) {
        val dependencies = allDuplicates[id]
        if (dependencies.size > 1) {
          val duplicateDependencies: MutableList<MavenDomDependency> = ArrayList()

          for (d in dependencies) {
            if (d === dependency) continue

            if (d.parent === dependency.parent) {
              // Dependencies in the same file must be unique by groupId:artifactId:type:classifier
              MavenLog.LOG.debug("Duplicate dependencies in the same file: ${dependencyToString(d)}")
              duplicateDependencies.add(d)
            }
            else {
              if (scope(d) == scope(dependency) && d.version.stringValue == dependency.version.stringValue) {
                // Dependencies in different files must not have same groupId:artifactId:VERSION:type:classifier:SCOPE
                MavenLog.LOG.debug("Duplicate dependencies in different files: ${dependencyToString(d)}")
                duplicateDependencies.add(d)
              }
            }
          }

          if (duplicateDependencies.size > 0) {
            addProblem(dependency, duplicateDependencies, holder)
          }
        }
      }
    }
  }

  private fun dependencyToString(d: MavenDomDependency) = "${d.groupId.stringValue}:${d.artifactId.stringValue}:${d.version.stringValue}"

  private fun scope(dependency: MavenDomDependency): String {
    val res = dependency.scope.rawText
    if (res.isNullOrEmpty()) return "compile"

    return res
  }

  private fun addProblem(dependency: MavenDomDependency, dependencies: Collection<MavenDomDependency>, holder: DomElementAnnotationHolder) {
    val processed: MutableSet<MavenDomProjectModel> = HashSet()
    val links: MutableList<String> = ArrayList()
    for (domDependency in dependencies) {
      if (dependency == domDependency) continue
      val model = domDependency.getParentOfType(MavenDomProjectModel::class.java, false)
      if (model != null && !processed.contains(model)) {
        links.add(createLinkText(model, domDependency))
        processed.add(model)
      }
    }
    links.sort()
    holder.createProblem(dependency, HighlightSeverity.WARNING,
                         MavenDomBundle.message("MavenDuplicateDependenciesInspection.has.duplicates", NlsMessages.formatAndList(links)))
  }

  private fun createLinkText(model: MavenDomProjectModel, dependency: MavenDomDependency): String {
    val projectName = MavenDomUtil.getProjectName(model)

    val tag = dependency.xmlTag
    val file = tag?.containingFile?.virtualFile

    if (file == null) return projectName

    return "<a href ='#navigation/${file.path}:${tag.textRange.startOffset}'>$projectName</a>"
  }

  private fun getDuplicateDependenciesMap(projectModel: MavenDomProjectModel): MultiMap<DependencyConflictId, MavenDomDependency> {
    val allDependencies = MultiMap.createSet<DependencyConflictId, MavenDomDependency>()

    val collectProcessor = Processor { model: MavenDomProjectModel ->
      collect(allDependencies, model.dependencies)
      false
    }

    MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, collectProcessor, true)
    MavenDomProjectProcessorUtils.processParentProjects(projectModel, collectProcessor)

    return allDependencies
  }

  private fun collect(duplicates: MultiMap<DependencyConflictId, MavenDomDependency>, dependencies: MavenDomDependencies) {
    for (dependency in dependencies.dependencies) {
      val mavenId = DependencyConflictId.create(dependency)
      if (mavenId == null) continue

      duplicates.putValue(mavenId, dependency)
    }
  }

  private fun checkManagedDependencies(projectModel: MavenDomProjectModel, holder: DomElementAnnotationHolder) {
    val duplicates = MultiMap.createSet<DependencyConflictId, MavenDomDependency>()
    collect(duplicates, projectModel.dependencyManagement.dependencies)

    for ((_, set) in duplicates.entrySet()) {
      if (set.size <= 1) continue

      for (dependency in set) {
        holder.createProblem(dependency, HighlightSeverity.WARNING, MavenProjectBundle.message("inspection.message.duplicated.dependency"))
      }
    }
  }
}