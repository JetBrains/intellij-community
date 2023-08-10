// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.inspections.incomplete;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleEditorBundle;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * @author Dmitry Batkovich
 */
public final class IncompletePropertyInspection extends LocalInspectionTool implements UnfairLocalInspectionTool {
  private static final @NonNls String SUFFIXES_TAG_NAME = "suffixes";
  public static final String TOOL_KEY = "IncompleteProperty";

  private final SortedSet<String> mySuffixes = new TreeSet<>();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySuffixes.clear();
    final Element element = node.getChild(SUFFIXES_TAG_NAME);
    if (element != null) {
      mySuffixes.addAll(StringUtil.split(element.getText(), ",", true, false));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!mySuffixes.isEmpty()) {
      node.addContent(new Element(SUFFIXES_TAG_NAME).setText(StringUtil.join(mySuffixes, ",")));
    }
  }

  @NotNull
  public Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> buildPropertyGroupVisitor(@NotNull ResourceBundle resourceBundle) {
    return properties -> !isPropertyComplete(properties, resourceBundle)
    ? new ResourceBundleEditorProblemDescriptor[]{new ResourceBundleEditorProblemDescriptor(ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                            ResourceBundleEditorBundle.message(
                                                                                              "incomplete.property.inspection.description",
                                                                                              properties[0].getName()),
                                                                                            new IgnoreLocalesQuickFix(properties[0],
                                                                                                                      resourceBundle))}
    : null;
  }

  @NotNull
  public static IncompletePropertyInspection getInstance(PsiElement element) {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(element.getProject());
    InspectionProfile inspectionProfile = profileManager.getCurrentProfile();
    return (IncompletePropertyInspection) inspectionProfile.getUnwrappedTool(TOOL_KEY, element);
  }

  private static class IgnoreLocalesQuickFix implements QuickFix<ResourceBundleEditorProblemDescriptor> {
    private final ResourceBundle myResourceBundle;
    private final SmartPsiElementPointer<PsiElement> myElementPointer;

    IgnoreLocalesQuickFix(IProperty property, ResourceBundle bundle) {
      myElementPointer = SmartPointerManager.getInstance(bundle.getProject()).createSmartPsiElementPointer(property.getPsiElement());
      myResourceBundle = bundle;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return ResourceBundleEditorBundle.message("incomplete.property.quick.fix.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ResourceBundleEditorProblemDescriptor descriptor) {
      final PsiElement element = myElementPointer.getElement();
      if (element == null) {
        return;
      }
      final IProperty property = PropertiesImplUtil.getProperty(element);
      if (property == null) {
        return;
      }

      final IncompletePropertyInspection inspection = getInstance(element);
      final List<PropertiesFile> allFilesWithoutTranslation = inspection.getPropertiesFilesWithoutTranslation(myResourceBundle, property.getKey());

      if (allFilesWithoutTranslation.isEmpty()) {
        return;
      }


      final TreeSet<String> suffixesToIgnore = new TreeSet<>(ContainerUtil.map(allFilesWithoutTranslation,
                                                                               PropertiesUtil::getSuffix));
      if (new IncompletePropertyInspectionOptionsPanel(suffixesToIgnore).showDialogAndGet(project)) {
        InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project,
                                                                         it -> ((IncompletePropertyInspection)it.getInspectionTool(TOOL_KEY, element).getTool()).addSuffixes(suffixesToIgnore));
      }
    }
  }

  public boolean isPropertyComplete(final String key, final ResourceBundle resourceBundle) {
    return isPropertyComplete(resourceBundle.getPropertiesFiles().stream().map(f -> f.findPropertyByKey(key)).filter(Objects::nonNull).toArray(IProperty[]::new), resourceBundle);
  }

  private boolean isPropertyComplete(final IProperty[] properties, final ResourceBundle resourceBundle) {
    final Set<PropertiesFile> existed = ContainerUtil.map2Set(properties, IProperty::getPropertiesFile);
    for (PropertiesFile file : resourceBundle.getPropertiesFiles()) {
      if (!existed.contains(file) && !getIgnoredSuffixes().contains(PropertiesUtil.getSuffix(file))) {
        return false;
      }
    }
    return true;
  }

  public Set<String> getIgnoredSuffixes() {
    return mySuffixes;
  }

  public List<PropertiesFile> getPropertiesFilesWithoutTranslation(final ResourceBundle resourceBundle, final String key) {
    return ContainerUtil.filter(resourceBundle.getPropertiesFiles(), propertiesFile -> propertiesFile.findPropertyByKey(key) == null &&
                                                                                   !getIgnoredSuffixes().contains(PropertiesUtil.getSuffix(propertiesFile)));
  }

  public void addSuffixes(Collection<String> suffixes) {
    mySuffixes.addAll(suffixes);
  }
}
