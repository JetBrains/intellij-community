/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.inspections.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class ResourceBundleEditorHighlighter implements BackgroundEditorHighlighter {
  private final static Logger LOG = Logger.getInstance(ResourceBundleEditorHighlighter.class);

  private final ResourceBundleEditor myEditor;

  public ResourceBundleEditorHighlighter(ResourceBundleEditor editor) {
    myEditor = editor;
  }

  @NotNull
  @Override
  public HighlightingPass[] createPassesForEditor() {
    return new HighlightingPass[]{new ResourceBundleEditorHighlightingPass(myEditor)};
  }

  @NotNull
  @Override
  public HighlightingPass[] createPassesForVisibleArea() {
    throw new UnsupportedOperationException();
  }

  private static class ResourceBundleEditorHighlightingPass implements HighlightingPass {
    private final ResourceBundleEditor myEditor;

    private ResourceBundleEditorHighlightingPass(ResourceBundleEditor editor) {
      myEditor = editor;
    }

    @Override
    public void collectInformation(@NotNull ProgressIndicator progress) {
      InspectionProfile profileToUse = InspectionProfileManager.getInstance().getCurrentProfile();
      final PsiFile containingFile = myEditor.getResourceBundle().getDefaultPropertiesFile().getContainingFile();
      final InspectionVisitorWrapper[] visitors =
        Arrays.stream(profileToUse.getInspectionTools(containingFile))
          .filter(t -> profileToUse.isToolEnabled(HighlightDisplayKey.find(t.getShortName()), containingFile))
          .map(InspectionToolWrapper::getTool)
          .filter(ResourceBundleEditorInspection.class::isInstance)
          .map(ResourceBundleEditorInspection.class::cast)
          .map(i -> {
            final HighlightDisplayKey key = HighlightDisplayKey.find(((InspectionProfileEntry)i).getShortName());
            return new InspectionVisitorWrapper(i.buildPropertyGroupVisitor(myEditor.getResourceBundle()),
                                                profileToUse.getErrorLevel(key, containingFile).getSeverity(),
                                                key);
          })
          .toArray(InspectionVisitorWrapper[]::new);

      final List<PropertiesFile> files = myEditor.getResourceBundle().getPropertiesFiles();
      final Project project = myEditor.getResourceBundle().getProject();

      final TreeVisitor<TreeElementWrapper> nodeVisitor =
        new TreeVisitor<TreeElementWrapper>() {
          @Override
          public boolean visit(@NotNull TreeElementWrapper wrapper) {
            final TreeElement treeElement = wrapper.getValue();
            if (!(treeElement instanceof ResourceBundlePropertyStructureViewElement)) return false;
            ResourceBundlePropertyStructureViewElement node = (ResourceBundlePropertyStructureViewElement) treeElement;
            final String key = node.getProperty().getKey();
            LOG.assertTrue(key != null);
            SortedSet<HighlightInfoType> highlightTypes = new TreeSet<>(Comparator.comparing(t -> t.getSeverity(null)));
            List<Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>> allDescriptors =
              new SmartList<>();
            final IProperty[] properties =
              files.stream().map(f -> f.findPropertyByKey(key)).filter(Objects::nonNull).toArray(IProperty[]::new);
            if (properties.length == 0) return false;
            for (InspectionVisitorWrapper v : visitors) {
              final ResourceBundleEditorProblemDescriptor[] problemDescriptors = v.getProblemVisitor().apply(properties);
              if (!ArrayUtil.isEmpty(problemDescriptors)) {
                final HighlightSeverity severity = v.getSeverity();
                for (ResourceBundleEditorProblemDescriptor descriptor : problemDescriptors) {
                  allDescriptors.add(Pair.create(descriptor, v.getKey()));
                  final HighlightInfoType infoType =
                    ProblemDescriptorUtil.getHighlightInfoType(descriptor.getHighlightType(),
                                                               severity,
                                                               SeverityRegistrar.getSeverityRegistrar(project));
                  highlightTypes.add(infoType);
                }
              }
            }
            node.setInspectedPropertyProblems(allDescriptors.isEmpty() ? null : new InspectedPropertyProblems(allDescriptors.toArray(new Pair[allDescriptors.size()]), highlightTypes));
            return false;
          }
        };
      myEditor.getStructureViewComponent().getTreeBuilder().accept(TreeElementWrapper.class,
                                                                   nodeVisitor);
    }

    @Override
    public void applyInformationToEditor() {
      myEditor.getStructureViewComponent().repaint();
    }
  }

  private static class InspectionVisitorWrapper {
    private final Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> myProblemVisitor;
    private final HighlightSeverity mySeverity;
    private final HighlightDisplayKey myKey;

    private InspectionVisitorWrapper(@NotNull Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> visitor,
                                     @NotNull HighlightSeverity severity,
                                     @NotNull HighlightDisplayKey key) {
      myProblemVisitor = visitor;
      mySeverity = severity;
      myKey = key;
    }

    public Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> getProblemVisitor() {
      return myProblemVisitor;
    }

    public HighlightSeverity getSeverity() {
      return mySeverity;
    }

    public HighlightDisplayKey getKey() {
      return myKey;
    }
  }
}
