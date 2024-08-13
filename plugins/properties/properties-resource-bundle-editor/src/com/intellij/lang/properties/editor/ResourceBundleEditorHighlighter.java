// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.unused.UnusedPropertyUtil;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection;
import com.intellij.lang.properties.editor.inspections.InspectedPropertyProblems;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.editor.inspections.incomplete.IncompletePropertyInspection;
import com.intellij.lang.properties.psi.PropertiesFile;
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

  private final ResourceBundleEditor myEditor;

  public ResourceBundleEditorHighlighter(ResourceBundleEditor editor) {
    myEditor = editor;
  }

  @Override
  public @NotNull HighlightingPass @NotNull [] createPassesForEditor() {
    return new HighlightingPass[]{new ResourceBundleEditorHighlightingPass(myEditor)};
  }

  private static final class ResourceBundleEditorHighlightingPass implements HighlightingPass {
    private final ResourceBundleEditor myEditor;

    private ResourceBundleEditorHighlightingPass(ResourceBundleEditor editor) {
      myEditor = editor;
    }

    @Override
    public void collectInformation(@NotNull ProgressIndicator progress) {
      InspectionProfile profileToUse = InspectionProfileManager.getInstance().getCurrentProfile();
      ResourceBundle rb = myEditor.getResourceBundle();
      if (!rb.isValid()) return;
      final PsiFile containingFile = rb.getDefaultPropertiesFile().getContainingFile();
      List<InspectionVisitorWrapper> visitors = new ArrayList<>();
      HighlightDisplayKey unusedKey = HighlightDisplayKey.find(UnusedPropertyInspection.SHORT_NAME);
      if (profileToUse.isToolEnabled(unusedKey, containingFile)) {
        visitors.add(new InspectionVisitorWrapper(
          UnusedPropertyUtil.buildPropertyGroupVisitor(rb),
          profileToUse.getErrorLevel(unusedKey, containingFile).getSeverity(),
          unusedKey));
      }
      HighlightDisplayKey incompleteKey = HighlightDisplayKey.find(IncompletePropertyInspection.TOOL_KEY);
      if (profileToUse.isToolEnabled(incompleteKey, containingFile)) {
        InspectionProfileEntry unwrappedTool = profileToUse.getUnwrappedTool(IncompletePropertyInspection.TOOL_KEY, containingFile);
        assert unwrappedTool != null;
        visitors.add(new InspectionVisitorWrapper(
          ((IncompletePropertyInspection)unwrappedTool).buildPropertyGroupVisitor(rb),
          profileToUse.getErrorLevel(incompleteKey, containingFile).getSeverity(),
          incompleteKey));
      }
      final List<PropertiesFile> files = rb.getPropertiesFiles();
      final Project project = rb.getProject();

      final StructureViewModel model = myEditor.getStructureViewComponent().getTreeModel();
      final Deque<TreeElement> queue = new ArrayDeque<>(1);
      queue.addLast(model.getRoot());
      while (!queue.isEmpty()) {
        final TreeElement treeElement = queue.removeFirst();
        if (treeElement instanceof PropertyBundleEditorStructureViewElement) {
          IProperty property = ((PropertyBundleEditorStructureViewElement)treeElement).getProperty();
          if (property == null) continue;
          final String key = property.getKey();
          if (key == null) continue;
          SortedSet<HighlightInfoType> highlightTypes = new TreeSet<>(Comparator.comparing(t -> t.getSeverity(null)));
          List<Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>> allDescriptors =
            new SmartList<>();
          final IProperty[] properties =
            files.stream().map(f -> f.findPropertyByKey(key)).filter(Objects::nonNull).toArray(IProperty[]::new);
          if (properties.length != 0) {
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
            ((PropertyBundleEditorStructureViewElement)treeElement).setInspectedPropertyProblems(allDescriptors.isEmpty()
                                              ? null
                                              : new InspectedPropertyProblems(allDescriptors.toArray(new Pair[0]),
                                                                              highlightTypes));
          }
        }
        for (TreeElement element : treeElement.getChildren()) {
          queue.addLast(element);
        }
      }
    }

    @Override
    public void applyInformationToEditor() {
      myEditor.getStructureViewComponent().repaint();
    }
  }

  private static final class InspectionVisitorWrapper {
    private final Function<? super IProperty[], ? extends ResourceBundleEditorProblemDescriptor[]> myProblemVisitor;
    private final HighlightSeverity mySeverity;
    private final HighlightDisplayKey myKey;

    private InspectionVisitorWrapper(@NotNull Function<? super IProperty[], ? extends ResourceBundleEditorProblemDescriptor[]> visitor,
                                     @NotNull HighlightSeverity severity,
                                     @NotNull HighlightDisplayKey key) {
      myProblemVisitor = visitor;
      mySeverity = severity;
      myKey = key;
    }

    public Function<? super IProperty[], ? extends ResourceBundleEditorProblemDescriptor[]> getProblemVisitor() {
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
