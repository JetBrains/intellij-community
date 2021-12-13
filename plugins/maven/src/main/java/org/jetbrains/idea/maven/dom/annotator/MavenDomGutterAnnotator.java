// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MavenDomGutterAnnotator implements Annotator {

  private static void annotateDependencyUsages(@NotNull MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    final Set<MavenDomDependency> children = MavenDomProjectProcessorUtils.searchDependencyUsages(dependency);
    if (children.size() > 0) {
      final NavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod, DependencyConverter.INSTANCE);
      iconBuilder.
        setTargets(children).
        setPopupTitle(MavenDomBundle.message("navigate.parent.dependency.title")).
        setCellRenderer(MyListCellRenderer::new).
        setTooltipText(MavenDomBundle.message("overriding.dependency.title")).
        createGutterIcon(holder, dependency.getXmlTag());
    }
  }

  private static void annotateManagedDependency(MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    MavenDomDependency managingDependency = getManagingDependency(dependency);
    if (managingDependency != null) {

      final NavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod, DependencyConverter.INSTANCE);
      iconBuilder.
        setTargets(managingDependency).
        setTooltipText(generateTooltip(managingDependency)).
        createGutterIcon(holder, tag);
    }
  }

  @Nullable
  private static MavenDomDependency getManagingDependency(@NotNull MavenDomDependency dependency) {
    Project project = dependency.getManager().getProject();
    return MavenDomProjectProcessorUtils.searchManagingDependency(dependency, project);
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof XmlTag) {
      final DomElement element = DomManager.getDomManager(psiElement.getProject()).getDomElement((XmlTag)psiElement);
      if (element instanceof MavenDomDependency) {
        if (element.getParentOfType(MavenDomPlugin.class, true) != null) return;

        MavenDomDependency dependency = (MavenDomDependency)element;
        if (isDependencyManagementSection(dependency)) {
          annotateDependencyUsages(dependency, holder);
        }
        else {
          annotateManagedDependency(dependency, holder);
        }
      }
      else if (element instanceof MavenDomParent) {
        annotateMavenDomParent((MavenDomParent)element, holder);
      }
      else if (element instanceof MavenDomProjectModel) {
        annotateMavenDomProjectChildren((MavenDomProjectModel)element, holder);
      }
      else if (element instanceof MavenDomPlugin) {
        annotateMavenDomPlugin((MavenDomPlugin)element, holder);
      }
    }
  }

  private static void annotateMavenDomPlugin(@NotNull MavenDomPlugin plugin, @NotNull AnnotationHolder holder) {
    XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
    if (xmlTag == null) return;

    DomElement plugins = plugin.getParent();
    if (plugins == null) return;

    DomElement parent = plugins.getParent();
    if (parent instanceof MavenDomPluginManagement) {
      annotateMavenDomPluginInManagement(plugin, holder);
      return;
    }

    MavenDomPlugin managingPlugin = MavenDomProjectProcessorUtils.searchManagingPlugin(plugin);

    if (managingPlugin != null) {
      NavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
        NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod, PluginConverter.INSTANCE);

      iconBuilder.
        setTargets(Collections.singletonList(managingPlugin)).
        setTooltipText(MavenDomBundle.message("overriden.plugin.title")).
        createGutterIcon(holder, xmlTag);
    }
  }

  private static void annotateMavenDomPluginInManagement(@NotNull MavenDomPlugin plugin, @NotNull AnnotationHolder holder) {
    XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
    if (xmlTag == null) return;

    Collection<MavenDomPlugin> children = MavenDomProjectProcessorUtils.searchManagedPluginUsages(plugin);

    if (children.size() > 0) {
      NavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
        NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod, PluginConverter.INSTANCE);

      iconBuilder.
        setTargets(children).
        setPopupTitle(MavenDomBundle.message("navigate.parent.plugin.title")).
        setCellRenderer(MyListCellRenderer::new).
        setTooltipText(MavenDomBundle.message("overriding.plugin.title")).
        createGutterIcon(holder, xmlTag);
    }
  }


  private static void annotateMavenDomParent(@NotNull MavenDomParent mavenDomParent, @NotNull AnnotationHolder holder) {
    MavenDomProjectModel parent = MavenDomProjectProcessorUtils.findParent(mavenDomParent, mavenDomParent.getManager().getProject());

    if (parent != null) {
      NavigationGutterIconBuilder.create(MavenIcons.ParentProject, MavenProjectConverter.INSTANCE).
        setTargets(parent).
        setTooltipText(MavenDomBundle.message("parent.pom.title")).
        createGutterIcon(holder, mavenDomParent.getXmlElement());
    }
  }

  private static void annotateMavenDomProjectChildren(MavenDomProjectModel model, AnnotationHolder holder) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      Set<MavenDomProjectModel> children = MavenDomProjectProcessorUtils.getChildrenProjects(model);

      if (children.size() > 0) {
        NavigationGutterIconBuilder.create(MavenIcons.ChildrenProjects, MavenProjectConverter.INSTANCE).
          setTargets(children).
          setCellRenderer(MyListCellRenderer::new).
          setPopupTitle(MavenDomBundle.message("navigate.children.poms.title")).
          setTooltipText(MavenDomBundle.message("children.poms.title")).
          createGutterIcon(holder, model.getXmlElement());
      }
    }
  }

  private static boolean isDependencyManagementSection(@NotNull MavenDomDependency dependency) {
    return dependency.getParentOfType(MavenDomDependencyManagement.class, false) != null;
  }

  @NlsContexts.DetailedDescription
  private static String generateTooltip(MavenDomDependency dependency) {
    StringBuilder res = new StringBuilder();

    res.append("<dependency>\n");
    res.append("    <groupId>").append(dependency.getGroupId().getStringValue()).append("</groupId>\n");
    res.append("    <artifactId>").append(dependency.getArtifactId().getStringValue()).append("</artifactId>\n");

    if (dependency.getType().getXmlElement() != null) {
      res.append("    <type>").append(dependency.getType().getStringValue()).append("</type>\n");
    }

    if (dependency.getClassifier().getXmlElement() != null) {
      res.append("    <classifier>").append(dependency.getClassifier().getStringValue()).append("</classifier>\n");
    }

    if (dependency.getScope().getXmlElement() != null) {
      res.append("    <scope>").append(dependency.getScope().getStringValue()).append("</scope>\n");
    }

    if (dependency.getOptional().getXmlElement() != null) {
      res.append("    <optional>").append(dependency.getOptional().getStringValue()).append("</optional>\n");
    }

    if (dependency.getVersion().getXmlElement() != null) {
      res.append("    <version>").append(dependency.getVersion().getStringValue()).append("</version>\n");
    }

    res.append("</dependency>");

    return StringUtil.escapeXmlEntities(res.toString()).replace(" ", "&nbsp;"); //NON-NLS
  }

  private static class MyListCellRenderer extends PsiElementListCellRenderer<XmlTag> {
    @Override
    public String getElementText(XmlTag tag) {
      DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (domElement != null) {
        MavenDomProjectModel model = domElement.getParentOfType(MavenDomProjectModel.class, false);
        if (model != null) {
          MavenProject mavenProject = MavenDomUtil.findProject(model);
          if (mavenProject != null) return mavenProject.getDisplayName();

          String name = model.getName().getStringValue();
          if (!StringUtil.isEmptyOrSpaces(name)) {
            return name;
          }
        }
      }

      return tag.getContainingFile().getName();
    }

    @Override
    protected String getContainerText(XmlTag element, String name) {
      return null;
    }

    @Override
    protected Icon getIcon(PsiElement element) {
      return MavenIcons.MavenProject;
    }
  }

  private static class DependencyConverter implements NotNullFunction<MavenDomDependency, Collection<? extends PsiElement>> {
    public static final DependencyConverter INSTANCE = new DependencyConverter();

    @Override
    @NotNull
    public Collection<? extends PsiElement> fun(final MavenDomDependency pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }

  private static class PluginConverter implements NotNullFunction<MavenDomPlugin, Collection<? extends PsiElement>> {
    public static final PluginConverter INSTANCE = new PluginConverter();

    @Override
    @NotNull
    public Collection<? extends PsiElement> fun(final MavenDomPlugin pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }

  private static class MavenProjectConverter implements NotNullFunction<MavenDomProjectModel, Collection<? extends PsiElement>> {
    public static final MavenProjectConverter INSTANCE = new MavenProjectConverter();

    @Override
    @NotNull
    public Collection<? extends PsiElement> fun(final MavenDomProjectModel pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }
}
