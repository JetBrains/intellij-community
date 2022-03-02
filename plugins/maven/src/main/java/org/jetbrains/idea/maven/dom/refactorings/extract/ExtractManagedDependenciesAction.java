// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.refactorings.extract;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.util.Function;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusion;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusions;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ExtractManagedDependenciesAction extends BaseRefactoringAction {

  public ExtractManagedDependenciesAction() {
    setInjectedContext(true);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new MyRefactoringActionHandler();
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return MavenDomUtil.isMavenFile(file);
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    if (!super.isAvailableOnElementInEditorAndFile(element, editor, file, context)) return false;
    return findDependencyAndParent(file, editor) != null;
  }

  private static Pair<MavenDomDependency, Set<MavenDomProjectModel>> findDependencyAndParent(PsiFile file, Editor editor) {
    final MavenDomDependency dependency = DomUtil.findDomElement(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                 MavenDomDependency.class);
    if (dependency == null || isManagedDependency(dependency)) return null;

    Set<MavenDomProjectModel> parents = getParentProjects(file);
    if (parents.isEmpty()) return null;

    return Pair.create(dependency, parents);
  }

  @NotNull
  private static Set<MavenDomProjectModel> getParentProjects(@NotNull PsiFile file) {
    final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

    if (model == null) return Collections.emptySet();
    return MavenDomProjectProcessorUtils.collectParentProjects(model);
  }

  private static boolean isManagedDependency(@NotNull MavenDomDependency dependency) {
    return MavenDomProjectProcessorUtils.searchManagingDependency(dependency) != null;
  }

  private static class MyRefactoringActionHandler implements RefactoringActionHandler {
    @Override
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
      MavenActionsUsagesCollector.trigger(project, MavenActionsUsagesCollector.EXTRACT_MANAGED_DEPENDENCIES);
      Pair<MavenDomDependency, Set<MavenDomProjectModel>> depAndParents = findDependencyAndParent(file, editor);
      if (depAndParents == null) return;

      final MavenDomDependency dependency = depAndParents.first;
      Set<MavenDomProjectModel> parent = depAndParents.second;

      Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences = getOccurencesFunction(dependency);
      final ProcessData processData = getProcessData(project, parent, funOccurrences, dependency.getExclusions().getXmlElement() != null);
      if (processData == null) return;

      final MavenDomProjectModel model = processData.getModel();
      final Set<MavenDomDependency> usages = processData.getUsages();
      final boolean extractExclusions = processData.isExtractExclusions();


      assert model != null;
      assert usages != null;

      WriteCommandAction.writeCommandAction(project, getFiles(file, model, usages)).run(() -> {
        MavenDomDependency addedDependency = model.getDependencyManagement().getDependencies().addDependency();
        addedDependency.getGroupId().setStringValue(dependency.getGroupId().getStringValue());
        addedDependency.getArtifactId().setStringValue(dependency.getArtifactId().getStringValue());
        addedDependency.getVersion().setStringValue(dependency.getVersion().getStringValue());
        String typeValue = dependency.getType().getStringValue();

        dependency.getVersion().undefine();

        if (typeValue != null) {
          addedDependency.getType().setStringValue(typeValue);
        }

        String classifier = dependency.getClassifier().getStringValue();
        if (classifier != null) {
          addedDependency.getClassifier().setStringValue(classifier);
        }

        String systemPath = dependency.getSystemPath().getStringValue();
        if (systemPath != null) {
          addedDependency.getSystemPath().setStringValue(systemPath);
          dependency.getSystemPath().undefine();
        }


        if (extractExclusions) {
          MavenDomExclusions addedExclusions = addedDependency.getExclusions();
          for (MavenDomExclusion exclusion : dependency.getExclusions().getExclusions()) {
            MavenDomExclusion domExclusion = addedExclusions.addExclusion();

            domExclusion.getGroupId().setStringValue(exclusion.getGroupId().getStringValue());
            domExclusion.getArtifactId().setStringValue(exclusion.getArtifactId().getStringValue());
          }

          dependency.getExclusions().undefine();
        }

        for (MavenDomDependency usage : usages) {
          usage.getVersion().undefine();
        }
      });
    }

    private static PsiFile[] getFiles(@NotNull PsiFile file, @NotNull MavenDomProjectModel model, @NotNull Set<? extends MavenDomDependency> usages) {
      Set<PsiFile> files = new HashSet<>();

      files.add(file);
      XmlElement xmlElement = model.getXmlElement();
      if (xmlElement != null) files.add(xmlElement.getContainingFile());
      for (MavenDomDependency usage : usages) {
        XmlElement element = usage.getXmlElement();
        if (element != null) {
          files.add(element.getContainingFile());
        }
      }

      return PsiUtilCore.toPsiFileArray(files);
    }


    @Nullable
    private static ProcessData getProcessData(@NotNull Project project,

                                              @NotNull Set<MavenDomProjectModel> models,
                                              @NotNull Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences,
                                              boolean hasExclusions) {
      if (models.size() == 0) return null;

      if (models.size() == 1 && !hasExclusions) {
        MavenDomProjectModel model = models.iterator().next();
        if (funOccurrences.fun(model).size() == 0) {
          return new ProcessData(model, Collections.emptySet(), false);
        }
      }

      SelectMavenProjectDialog dialog = new SelectMavenProjectDialog(project, models, funOccurrences, hasExclusions);
      dialog.show();

      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        MavenDomProjectModel model = dialog.getSelectedProject();

        return new ProcessData(model,
                               dialog.isReplaceAllOccurrences() ? funOccurrences.fun(model) : Collections.emptySet(),
                               dialog.isExtractExclusions());
      }

      return null;
    }

    private static Function<MavenDomProjectModel, Set<MavenDomDependency>> getOccurencesFunction(final MavenDomDependency dependency) {

      return model -> {
        DependencyConflictId dependencyId = DependencyConflictId.create(dependency);
        if (dependencyId == null) return Collections.emptySet();

        return MavenDomProjectProcessorUtils.searchDependencyUsages(model, dependencyId, Collections.singleton(dependency));
      };
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    }
  }

  private static class ProcessData {
    private final MavenDomProjectModel myModel;
    private final Set<MavenDomDependency> myUsages;
    private final boolean myExtractExclusions;

    public MavenDomProjectModel getModel() {
      return myModel;
    }

    public Set<MavenDomDependency> getUsages() {
      return myUsages;
    }

    public boolean isExtractExclusions() {
      return myExtractExclusions;
    }

    ProcessData(MavenDomProjectModel model, Set<MavenDomDependency> usages, boolean extractExclusions) {
      myModel = model;
      myUsages = usages;
      myExtractExclusions = extractExclusions;
    }
  }
}
