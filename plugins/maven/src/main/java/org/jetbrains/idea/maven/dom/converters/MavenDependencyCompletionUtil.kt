// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Processor
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.dom.model.*
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.*
import java.util.function.Function
import javax.swing.Icon

object MavenDependencyCompletionUtil {
  fun isPlugin(dependency: MavenDomShortArtifactCoordinates): Boolean {
    return dependency is MavenDomPlugin
  }

  fun findManagedPlugin(
    domModel: MavenDomProjectModel, project: Project,
    groupId: String, artifactId: String,
  ): MavenDomPlugin? {
    val ref = Ref<MavenDomPlugin?>()

    MavenDomProjectProcessorUtils.processPluginsInPluginManagement(domModel, Processor { plugin: MavenDomPlugin? ->
      if (groupId == plugin!!.groupId.stringValue
          && artifactId == plugin.artifactId.stringValue
          && null != plugin.version.stringValue
      ) {
        ref.set(plugin)
        return@Processor true
      }
      false
    }, project)

    return ref.get()
  }

  @JvmStatic
  fun findManagedDependency(
    domModel: MavenDomProjectModel, project: Project,
    groupId: String, artifactId: String,
  ): MavenDomDependency? {
    val ref = Ref<MavenDomDependency?>()

    MavenDomProjectProcessorUtils.processDependenciesInDependencyManagement(domModel, Processor { dependency: MavenDomDependency? ->
      if (groupId == dependency!!.groupId.stringValue
          &&
          artifactId == dependency.artifactId.stringValue
      ) {
        ref.set(dependency)
        return@Processor true
      }
      false
    }, project)

    return ref.get()
  }

  @JvmStatic
  fun isInsideManagedDependency(dependency: MavenDomShortArtifactCoordinates): Boolean {
    val parent = dependency.parent
    if (parent !is MavenDomDependencies) return false

    return parent.parent is MavenDomDependencyManagement
  }

  @JvmStatic
  fun invokeCompletion(context: InsertionContext, completionType: CompletionType) {
    context.laterRunnable = Runnable { CodeCompletionHandlerBase(completionType).invokeCompletion(context.project, context.editor) }
  }

  fun lookupElement(item: MavenDependencyCompletionItem, lookup: String): LookupElementBuilder {
    return LookupElementBuilder.create(item, lookup)
      .withIcon(getIcon(item.type))
  }

  @JvmStatic
  fun getMaxIcon(searchResult: MavenArtifactSearchResult): MavenDependencyCompletionItem {
    return Collections.max(
      listOf(*searchResult.searchResults.items),
      Comparator.comparing(Function { r: MavenDependencyCompletionItem ->
        if (r.type == null) {
          return@Function Int.MIN_VALUE
        }
        r.type!!.weight
      }))
  }

  @JvmOverloads
  fun lookupElement(info: MavenRepositoryArtifactInfo, presentableText: String = getPresentableText(info)): LookupElementBuilder {
    val elementBuilder = LookupElementBuilder.create(info, getLookupString(info))
      .withPresentableText(presentableText)
    elementBuilder.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
    if (info.items.size == 1) {
      return elementBuilder.withIcon(getIcon(info.items[0].type))
    }
    return elementBuilder
  }

  fun getPresentableText(info: MavenRepositoryArtifactInfo): String {
    if (info.items.size == 1) {
      return getLookupString(info.items[0])
    }
    return IndicesBundle.message("maven.dependency.completion.presentable", info.groupId, info.artifactId)
  }

  @JvmStatic
  fun getIcon(type: MavenDependencyCompletionItem.Type?): Icon? {
    if (type == MavenDependencyCompletionItem.Type.PROJECT) {
      return AllIcons.Nodes.Module
    }
    return null
  }

  fun getLookupString(info: MavenRepositoryArtifactInfo): String {
    val infoItems = info.items
    if (infoItems.isNotEmpty()) {
      return getLookupString(infoItems[0])
    }
    return info.groupId + ":" + info.artifactId
  }

  fun getLookupString(description: MavenDependencyCompletionItem): String {
    val builder = StringBuilder(description.groupId)
    if (description.artifactId == null) {
      builder.append(":...")
    }
    else {
      builder.append(":").append(description.artifactId)
      if (description.packaging != null) {
        builder.append(":").append(description.packaging)
      }
      if (description.version != null) {
        builder.append(":").append(description.version)
      }
      else {
        builder.append(":...")
      }
    }
    return builder.toString()
  }

  fun removeDummy(str: String?): String {
    if (str == null) {
      return ""
    }
    return StringUtil.trim(str.replace(CompletionUtil.DUMMY_IDENTIFIER, "").replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, ""))
  }
}
