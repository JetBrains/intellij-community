/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencyManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.utils.MavenIcons;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MavenDomGutterAnnotator implements Annotator {

  private static final PsiElementListCellRenderer<XmlTag> RENDERER = new PsiElementListCellRenderer<XmlTag>() {
    @Override
    public String getElementText(XmlTag tag) {
      DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (domElement != null) {
        MavenDomProjectModel model = domElement.getParentOfType(MavenDomProjectModel.class, false);
        if (model != null) {
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
      return MavenIcons.MAVEN_PROJECT_ICON;
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  };

  private static final NotNullFunction<MavenDomDependency, Collection<? extends PsiElement>> CONVERTER =
    new NotNullFunction<MavenDomDependency, Collection<? extends PsiElement>>() {

      @NotNull
      public Collection<? extends PsiElement> fun(final MavenDomDependency pointer) {
        return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
      }
    };

  private static void annotateDependencyUsages(@NotNull MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    final Set<MavenDomDependency> children = getDependencyUsages(dependency);
    if (children.size() > 0) {
      final NavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        NavigationGutterIconBuilder.create(MavenIcons.OVERRIDEN_DEPENDENCY, CONVERTER);
      iconBuilder.
        setTargets(children).
        setPopupTitle(MavenDomBundle.message("navigate.parent.dependency.title")).
        setCellRenderer(RENDERER).
        setTooltipText(MavenDomBundle.message("overriding.dependency.title")).
        install(holder, dependency.getXmlTag());
    }
  }



  private static void annotateManagedDependency(MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    final List<MavenDomDependency> children = getManagingDependencies(dependency);
    if (children.size() > 0) {

      final NavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        NavigationGutterIconBuilder.create(MavenIcons.OVERRIDING_DEPENDENCY, CONVERTER);
      iconBuilder.
        setTargets(children).
        setTooltipText(MavenDomBundle.message("overriden.dependency.title")).
        install(holder, dependency.getXmlTag());
    }
  }

  private static List<MavenDomDependency> getManagingDependencies(@NotNull MavenDomDependency dependency) {
    Project project = dependency.getManager().getProject();
    MavenDomDependency parentDependency = MavenDomProjectProcessorUtils.searchManagingDependency(dependency, project);

    if (parentDependency != null) {
      return Collections.singletonList(parentDependency);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Set<MavenDomDependency> getDependencyUsages(@NotNull MavenDomDependency dependency) {
    return MavenDomProjectProcessorUtils.searchDependencyUsages(dependency, dependency.getManager().getProject());
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof XmlTag) {
      final DomElement element = DomManager.getDomManager(psiElement.getProject()).getDomElement((XmlTag)psiElement);
      if (element instanceof MavenDomDependency) {
        MavenDomDependency dependency = (MavenDomDependency)element;
        if (isDependencyManagementSection(dependency)) {
           annotateDependencyUsages(dependency, holder);
        } else {
          annotateManagedDependency(dependency, holder);
        }
      }
    }
  }

  private static boolean isDependencyManagementSection(@NotNull MavenDomDependency dependency) {
    return dependency.getParentOfType(MavenDomDependencyManagement.class, false) != null;
  }
}