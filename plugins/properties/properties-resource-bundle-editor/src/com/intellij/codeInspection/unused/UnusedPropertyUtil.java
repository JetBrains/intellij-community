// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unused;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class UnusedPropertyUtil {
  private static final Logger LOG = Logger.getInstance(UnusedPropertyUtil.class);
  @NotNull
  public static Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> buildPropertyGroupVisitor(@NotNull ResourceBundle resourceBundle) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(resourceBundle.getDefaultPropertiesFile().getContainingFile());
    if (module == null) return x -> null;
    final UnusedPropertyInspection.UnusedPropertiesSearchHelper helper = new UnusedPropertyInspection.UnusedPropertiesSearchHelper(module);

    return properties -> !UnusedPropertyInspection.isPropertyUsed((Property)properties[0], helper, true) ? new ResourceBundleEditorProblemDescriptor[]{
      new ResourceBundleEditorProblemDescriptor(ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                PropertiesBundle.message("unused.property.problem.descriptor.name"),
                                                new RemovePropertiesFromAllLocalesFix((Property)properties[0]))} : null;
  }

  private static final class RemovePropertiesFromAllLocalesFix implements QuickFix<ResourceBundleEditorProblemDescriptor> {
    private final SmartPsiElementPointer<Property> myRepresentativePointer;

    private RemovePropertiesFromAllLocalesFix(Property property) {
      myRepresentativePointer = SmartPointerManager.getInstance(property.getProject()).createSmartPsiElementPointer(property);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return PropertiesBundle.message("remove.property.intention.text");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ResourceBundleEditorProblemDescriptor descriptor) {
      final Property element = myRepresentativePointer.getElement();
      if (element == null) return;
      final String key = element.getKey();
      if (key == null) return;
      final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(myRepresentativePointer.getContainingFile());
      LOG.assertTrue(file != null);
      List<PropertiesFile> propertiesFiles = file.getResourceBundle().getPropertiesFiles();
      if (!FileModificationService.getInstance()
        .preparePsiElementsForWrite(ContainerUtil.map2List(propertiesFiles, p -> p.getContainingFile()))) {
        return;
      }
      WriteAction.run(() -> propertiesFiles
        .stream()
        .flatMap(f -> f.findPropertiesByKey(key).stream())
        .filter(Objects::nonNull)
        .map(IProperty::getPsiElement)
        .forEach(e -> e.delete())
      );
    }
  }
}
