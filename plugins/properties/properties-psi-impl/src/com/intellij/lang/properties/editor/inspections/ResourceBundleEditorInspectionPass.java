/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.InspectionProjectProfileManagerImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorInspectionPass {
  @Nullable
  public static InspectionPassInfo inspect(@NotNull final String key, ResourceBundle resourceBundle) {
    final List<IProperty> properties =
      ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(), new Function<PropertiesFile, IProperty>() {
        @Override
        public IProperty fun(PropertiesFile propertiesFile) {
          return propertiesFile.findPropertyByKey(key);
        }
      });

    if (properties.isEmpty()) {
      return null;
    }
    final IProperty property = properties.get(0);
    final PsiElement representativeElement = property.getPsiElement();
    final PsiFile representativeFile = representativeElement.getContainingFile();

    final Project project = representativeElement.getProject();

    InspectionProfileWrapper profileToUse = InspectionProjectProfileManagerImpl.getInstanceImpl(project).getProfileWrapper();
    final PsiFile containingFile = representativeFile.getContainingFile();
    final InspectionToolWrapper[] propertiesTools = profileToUse.getInspectionTools(containingFile);

    List<Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>> allDescriptors =
      new SmartList<Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>>();
    SortedSet<HighlightInfoType> highlightTypes = new TreeSet<HighlightInfoType>(new Comparator<HighlightInfoType>() {
      @Override
      public int compare(HighlightInfoType o1, HighlightInfoType o2) {
        final HighlightSeverity s1 = o1.getSeverity(null);
        final HighlightSeverity s2 = o2.getSeverity(null);
        return Comparing.compare(s1, s2);
      }
    });

    for (InspectionToolWrapper tool : propertiesTools) {
      final HighlightDisplayKey toolKey;
      if (tool.getTool() instanceof ResourceBundleEditorInspection &&
          profileToUse.isToolEnabled(toolKey = HighlightDisplayKey.find(tool.getShortName()), containingFile)) {
        final ResourceBundleEditorInspection inspection = (ResourceBundleEditorInspection)tool.getTool();
        final ResourceBundleEditorProblemDescriptor[] descriptors = inspection.checkPropertyGroup(properties, resourceBundle);
        if (descriptors != null) {
          for (ResourceBundleEditorProblemDescriptor descriptor : descriptors) {
            final QuickFix[] currentFixes = descriptor.getFixes();
            if (currentFixes != null) {
              allDescriptors.add(Pair.create(descriptor, toolKey));
            }
            HighlightSeverity severity = profileToUse.getInspectionProfile().getErrorLevel(toolKey, containingFile).getSeverity();
            final HighlightInfoType infoType =
              ProblemDescriptorUtil.getHighlightInfoType(descriptor.getHighlightType(),
                                                         severity,
                                                         SeverityRegistrar.getSeverityRegistrar(project));
            highlightTypes.add(infoType);
          }
        }
      }
    }
    return new InspectionPassInfo(allDescriptors.toArray(new Pair[allDescriptors.size()]), highlightTypes);
  }

  public static class InspectionPassInfo {
    private final Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] myDescriptors;
    private final SortedSet<HighlightInfoType> myHighlightTypes;

    public InspectionPassInfo(Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] descriptors, SortedSet<HighlightInfoType> types) {
      myDescriptors = descriptors;
      myHighlightTypes = types;
    }

    public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] getDescriptors() {
      return myDescriptors;
    }

    @Nullable
    public TextAttributes getTextAttributes(EditorColorsScheme scheme) {
      TextAttributes mixedAttributes = null;
      for (HighlightInfoType type : myHighlightTypes) {
        final TextAttributes current = scheme.getAttributes(type.getAttributesKey());
        if (mixedAttributes == null) {
          mixedAttributes = current;
        } else {
          mixedAttributes = TextAttributes.merge(mixedAttributes, current);
        }
      }
      return mixedAttributes;
    }
  }
}
