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
package com.intellij.lang.properties.editor.inspections.incomplete;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorInspection;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.*;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class IncompletePropertyInspection extends LocalInspectionTool implements ResourceBundleEditorInspection {
  private static final String SUFFIXES_TAG_NAME = "suffixes";
  private static final String TOOL_KEY = "IncompleteProperty";

  SortedSet<String> mySuffixes = new TreeSet<String>();

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new IncompletePropertyInspectionOptionsPanel(mySuffixes).buildPanel();
  }

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

  @Nullable
  @Override
  public ResourceBundleEditorProblemDescriptor[] checkPropertyGroup(@NotNull List<IProperty> properties, @NotNull ResourceBundle resourceBundle) {
    return !isPropertyComplete(properties, resourceBundle)
           ? new ResourceBundleEditorProblemDescriptor[] {new ResourceBundleEditorProblemDescriptor(ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                                    PropertiesBundle.message("incomplete.property.inspection.description",
                                                                                           properties.get(0).getName()),
                                                                                                    new IgnoreLocalesQuickFix(properties.get(0), resourceBundle))}
           : null;
  }

  @NotNull
  public static IncompletePropertyInspection getInstance(PsiElement element) {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(element.getProject());
    InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    return (IncompletePropertyInspection) inspectionProfile.getUnwrappedTool(TOOL_KEY, element);
  }

  private static class IgnoreLocalesQuickFix implements QuickFix<ResourceBundleEditorProblemDescriptor> {
    private final ResourceBundle myResourceBundle;
    private final SmartPsiElementPointer<PsiElement> myElementPointer;

    public IgnoreLocalesQuickFix(IProperty property, ResourceBundle bundle) {
      myElementPointer = SmartPointerManager.getInstance(bundle.getProject()).createSmartPsiElementPointer(property.getPsiElement());
      myResourceBundle = bundle;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return PropertiesBundle.message("incomplete.property.quick.fix.name");
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


      final TreeSet<String> suffixesToIgnore = new TreeSet<String>(ContainerUtil.map(allFilesWithoutTranslation,
                                                                             new Function<PropertiesFile, String>() {
                                                                               @Override
                                                                               public String fun(PropertiesFile file) {
                                                                                 return PropertiesUtil.getSuffix(file);
                                                                               }
                                                                             }));
      if (new IncompletePropertyInspectionOptionsPanel(suffixesToIgnore).showDialogAndGet(project)) {
        DisableInspectionToolAction.modifyAndCommitProjectProfile(new Consumer<ModifiableModel>() {
          @Override
          public void consume(ModifiableModel modifiableModel) {
            ((IncompletePropertyInspection)modifiableModel.getInspectionTool(TOOL_KEY, element).getTool()).addSuffixes(suffixesToIgnore);
          }
        }, project);
      }
    }
  }

  public boolean isPropertyComplete(final String key, final ResourceBundle resourceBundle) {
    return isPropertyComplete(ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(), new Function<PropertiesFile, IProperty>() {
      @Override
      public IProperty fun(PropertiesFile file) {
        return file.findPropertyByKey(key);
      }
    }), resourceBundle);
  }

  private boolean isPropertyComplete(final List<IProperty> properties, final ResourceBundle resourceBundle) {
    final Set<PropertiesFile> existed = ContainerUtil.map2Set(properties, new Function<IProperty, PropertiesFile>() {

      @Override
      public PropertiesFile fun(IProperty property) {
        return property.getPropertiesFile();
      }
    });
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
    return ContainerUtil.filter(resourceBundle.getPropertiesFiles(), new Condition<PropertiesFile>() {
      @Override
      public boolean value(PropertiesFile propertiesFile) {
        return propertiesFile.findPropertyByKey(key) == null &&
               !getIgnoredSuffixes().contains(PropertiesUtil.getSuffix(propertiesFile));
      }
    });
  }

  public void addSuffixes(Collection<String> suffixes) {
    mySuffixes.addAll(suffixes);
  }
}
