// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.annotator

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.NotNullFunction
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xml.DomManager
import icons.MavenIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.annotator.MavenDomGutterAnnotatorLogger.log
import org.jetbrains.idea.maven.dom.model.*
import org.jetbrains.idea.maven.utils.MavenLog
import javax.swing.Icon

private class MavenDomGutterAnnotator : Annotator {
  override fun annotate(psiElement: PsiElement, holder: AnnotationHolder) {
    if (psiElement is XmlTag) {
      log { "MavenDomGutterAnnotator.annotate ${psiElement.name}" }
      val element = DomManager.getDomManager(psiElement.getProject()).getDomElement(psiElement)
      if (element is MavenDomDependency) {
        if (element.getParentOfType(MavenDomPlugin::class.java, true) != null) return

        if (isDependencyManagementSection(element)) {
          annotateDependencyUsages(element, holder)
        }
        else {
          annotateManagedDependency(element, holder)
        }
      }
      else if (element is MavenDomParent) {
        annotateMavenDomParent(element, holder)
      }
      else if (element is MavenDomProjectModel) {
        annotateMavenDomProjectChildren(element, holder)
      }
      else if (element is MavenDomPlugin) {
        annotateMavenDomPlugin(element, holder)
      }
    }
  }

  private class MyListCellRenderer : PsiElementListCellRenderer<XmlTag?>() {
    override fun getElementText(tag: XmlTag?): String? {
      if (null == tag) return null
      val domElement = DomManager.getDomManager(tag.project).getDomElement(tag)
      if (domElement != null) {
        val model = domElement.getParentOfType(MavenDomProjectModel::class.java, false)
        if (model != null) {
          val mavenProject = MavenDomUtil.findProject(model)
          if (mavenProject != null) return mavenProject.displayName

          val name = model.name.stringValue
          if (!name.isNullOrBlank()) {
            return name
          }
        }
      }

      return tag.containingFile.name
    }

    override fun getContainerText(element: XmlTag?, name: String): String? {
      return null
    }

    override fun getIcon(element: PsiElement): Icon {
      return MavenIcons.MavenProject
    }
  }

  private class DependencyConverter {
    companion object {
      val INSTANCE: NotNullFunction<MavenDomDependency, Collection<PsiElement>> = NotNullFunction { pointer ->
        ContainerUtil.createMaybeSingletonList(pointer.xmlTag)
      }
    }
  }

  private class PluginConverter {
    companion object {
      val INSTANCE: NotNullFunction<MavenDomPlugin, Collection<PsiElement>> = NotNullFunction { pointer ->
        ContainerUtil.createMaybeSingletonList(pointer.xmlTag)
      }
    }
  }

  private class MavenProjectConverter {
    companion object {
      val INSTANCE: NotNullFunction<MavenDomProjectModel, Collection<PsiElement>> = NotNullFunction { pointer ->
        ContainerUtil.createMaybeSingletonList(pointer.xmlTag)
      }
    }
  }

  private fun annotateDependencyUsages(dependency: MavenDomDependency, holder: AnnotationHolder) {
    val tag = dependency.xmlTag
    if (tag == null) return

    val children = MavenDomProjectProcessorUtils.searchDependencyUsages(dependency)
    if (children.isNotEmpty()) {
      val iconBuilder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod, DependencyConverter.INSTANCE)
      iconBuilder
        .setTargets(children)
        .setPopupTitle(MavenDomBundle.message("navigate.parent.dependency.title"))
        .setCellRenderer(Computable { MyListCellRenderer() })
        .setTooltipText(MavenDomBundle.message("overriding.dependency.title"))
        .createGutterIcon(holder, dependency.getXmlTag())
    }
  }

  private fun annotateManagedDependency(dependency: MavenDomDependency, holder: AnnotationHolder) {
    val tag = dependency.xmlTag
    if (tag == null) return

    val managingDependency = getManagingDependency(dependency)
    if (managingDependency != null) {
      val iconBuilder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod, DependencyConverter.INSTANCE)
      iconBuilder
        .setTargets(managingDependency)
        .setTooltipText(generateTooltip(managingDependency))
        .createGutterIcon(holder, tag)
    }
  }

  private fun getManagingDependency(dependency: MavenDomDependency): MavenDomDependency? {
    val project = dependency.manager.project
    return MavenDomProjectProcessorUtils.searchManagingDependency(dependency, project)
  }

  private fun annotateMavenDomPlugin(plugin: MavenDomPlugin, holder: AnnotationHolder) {
    val xmlTag = plugin.artifactId.xmlTag
    if (xmlTag == null) return

    val plugins = plugin.parent
    if (plugins == null) return

    val parent = plugins.parent
    if (parent is MavenDomPluginManagement) {
      annotateMavenDomPluginInManagement(plugin, holder)
      return
    }

    val managingPlugin = MavenDomProjectProcessorUtils.searchManagingPlugin(plugin)

    if (managingPlugin != null) {
      val iconBuilder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod, PluginConverter.INSTANCE)

      iconBuilder
        .setTargets(listOf(managingPlugin))
        .setTooltipText(MavenDomBundle.message("overriden.plugin.title"))
        .createGutterIcon(holder, xmlTag)
    }
  }

  private fun annotateMavenDomPluginInManagement(plugin: MavenDomPlugin, holder: AnnotationHolder) {
    val xmlTag = plugin.artifactId.xmlTag
    if (xmlTag == null) return

    val children = MavenDomProjectProcessorUtils.searchManagedPluginUsages(plugin)

    if (children.isNotEmpty()) {
      val iconBuilder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod, PluginConverter.INSTANCE)

      iconBuilder
        .setTargets(children)
        .setPopupTitle(MavenDomBundle.message("navigate.parent.plugin.title"))
        .setCellRenderer(Computable { MyListCellRenderer() })
        .setTooltipText(MavenDomBundle.message("overriding.plugin.title")).createGutterIcon(holder, xmlTag)
    }
  }


  private fun annotateMavenDomParent(mavenDomParent: MavenDomParent, holder: AnnotationHolder) {
    val parent = MavenDomProjectProcessorUtils.findParent(mavenDomParent, mavenDomParent.manager.project)

    if (parent != null) {
      NavigationGutterIconBuilder
        .create(MavenIcons.ParentProject, MavenProjectConverter.INSTANCE)
        .setTargets(parent)
        .setTooltipText(MavenDomBundle.message("parent.pom.title"))
        .createGutterIcon(holder, mavenDomParent.getXmlElement())
    }
  }

  private fun annotateMavenDomProjectChildren(model: MavenDomProjectModel, holder: AnnotationHolder) {
    val mavenProject = MavenDomUtil.findProject(model)
    if (mavenProject != null) {
      val children = MavenDomProjectProcessorUtils.getChildrenProjects(model)

      if (children.isNotEmpty()) {
        NavigationGutterIconBuilder
          .create(MavenIcons.ChildrenProjects, MavenProjectConverter.INSTANCE)
          .setTargets(children)
          .setCellRenderer(Computable { MyListCellRenderer() })
          .setPopupTitle(MavenDomBundle.message("navigate.children.poms.title"))
          .setTooltipText(MavenDomBundle.message("children.poms.title"))
          .createGutterIcon(holder, model.getXmlElement())
      }
    }
  }

  private fun isDependencyManagementSection(dependency: MavenDomDependency): Boolean {
    return dependency.getParentOfType(MavenDomDependencyManagement::class.java, false) != null
  }

  private fun generateTooltip(dependency: MavenDomDependency): @NlsContexts.DetailedDescription String {
    val res = StringBuilder()

    res.append("<dependency>\n")
    res.append("    <groupId>").append(dependency.groupId.stringValue).append("</groupId>\n")
    res.append("    <artifactId>").append(dependency.artifactId.stringValue).append("</artifactId>\n")

    if (dependency.type.xmlElement != null) {
      res.append("    <type>").append(dependency.type.stringValue).append("</type>\n")
    }

    if (dependency.classifier.xmlElement != null) {
      res.append("    <classifier>").append(dependency.classifier.stringValue).append("</classifier>\n")
    }

    if (dependency.scope.xmlElement != null) {
      res.append("    <scope>").append(dependency.scope.stringValue).append("</scope>\n")
    }

    if (dependency.optional.xmlElement != null) {
      res.append("    <optional>").append(dependency.optional.stringValue).append("</optional>\n")
    }

    if (dependency.version.xmlElement != null) {
      res.append("    <version>").append(dependency.version.stringValue).append("</version>\n")
    }

    res.append("</dependency>")

    return StringUtil.escapeXmlEntities(res.toString()).replace(" ", "&nbsp;") //NON-NLS
  }
}

@Internal
object MavenDomGutterAnnotatorLogger {
  private var logLevel: LogLevel = LogLevel.OFF

  @TestOnly
  fun resetLogLevel() {
    logLevel = LogLevel.OFF
  }

  @TestOnly
  fun setLogLevel(newLogLevel: LogLevel) {
    logLevel = newLogLevel
  }

  fun log(getText: () -> String) {
    if (logLevel == LogLevel.OFF) return
    val text = getText()
    when (logLevel) {
      LogLevel.OFF -> return
      LogLevel.ALL -> return
      LogLevel.ERROR -> MavenLog.LOG.error(text)
      LogLevel.WARNING -> MavenLog.LOG.warn(text)
      LogLevel.INFO -> MavenLog.LOG.info(text)
      LogLevel.DEBUG -> MavenLog.LOG.debug(text)
      LogLevel.TRACE -> MavenLog.LOG.trace(text)
    }
  }
}
