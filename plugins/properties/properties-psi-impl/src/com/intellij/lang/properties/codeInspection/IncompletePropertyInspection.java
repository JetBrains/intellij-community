// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IncompletePropertyInspection extends LocalInspectionTool {
  public static final String TOOL_KEY = "IncompleteProperty";

  private static final String SUFFIXES_TAG_NAME = "suffixes";

  public List<String> suffixes = new ArrayList<>();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    suffixes.clear();
    final Element element = node.getChild(SUFFIXES_TAG_NAME);
    if (element != null) {
      suffixes.addAll(StringUtil.split(element.getText(), ",", true, false));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!suffixes.isEmpty()) {
      node.addContent(new Element(SUFFIXES_TAG_NAME).setText(StringUtil.join(suffixes, ",")));
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.stringList("suffixes", PropertiesBundle.message("incomplete.property.inspection.ignored.suffixes.label"))
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (!(file instanceof PropertiesFile propertiesFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    ResourceBundle bundle = propertiesFile.getResourceBundle();
    List<PropertiesFile> bundleFiles = bundle.getPropertiesFiles();
    if (bundleFiles.size() <= 1) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    List<String> ignored = suffixes;
    List<PropertiesFile> filesToCheck = ContainerUtil.filter(bundleFiles,
      f -> !f.equals(propertiesFile) && !ignored.contains(PropertiesUtil.getSuffix(f)));
    if (filesToCheck.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof Property property)) return;
        String key = property.getUnescapedKey();
        if (key == null) return;

        List<String> missingSuffixes = new ArrayList<>();
        for (PropertiesFile f : filesToCheck) {
          if (f.findPropertyByKey(key) == null) {
            missingSuffixes.add(PropertiesUtil.getSuffix(f));
          }
        }
        if (!missingSuffixes.isEmpty()) {
          holder.registerProblem(
            property.getFirstChild(),
            PropertiesBundle.message("incomplete.property.inspection.description", key, StringUtil.join(missingSuffixes, ", ")),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            new AddMissingTranslationFix(),
            new IgnoreLocalesFix(missingSuffixes));
        }
      }
    };
  }

  private final class IgnoreLocalesFix extends ModCommandQuickFix {
    private final List<String> mySuffixes;

    private IgnoreLocalesFix(List<String> suffixesToIgnore) {
      mySuffixes = suffixesToIgnore;
    }

    @Override
    public @NotNull String getFamilyName() {
      return PropertiesBundle.message("incomplete.property.quick.fix.name");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return ModCommand.nop();
      return ModCommand.updateInspectionOption(element, IncompletePropertyInspection.this, inspection -> {
        for (String suffix : mySuffixes) {
          if (!inspection.suffixes.contains(suffix)) {
            inspection.suffixes.add(suffix);
          }
        }
      });
    }
  }

  private static final class AddMissingTranslationFix extends ModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return PropertiesBundle.message("add.missing.translation.quick.fix.family.name");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element.getParent() instanceof Property property)) return ModCommand.nop();

      String key = property.getUnescapedKey();
      if (key == null) return ModCommand.nop();
      String value = StringUtil.notNullize(property.getValue());

      PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(property.getContainingFile());
      if (propertiesFile == null) return ModCommand.nop();

      List<ModCommandAction> actions = new ArrayList<>();
      for (PropertiesFile f : propertiesFile.getResourceBundle().getPropertiesFiles()) {
        if (f.equals(propertiesFile)) continue;
        if (f.findPropertyByKey(key) != null) continue;
        Locale locale = f.getLocale();
        String localeName = locale.getDisplayLanguage();
        if (localeName.isEmpty()) localeName = PropertiesUtil.getSuffix(f);
        PsiFile targetPsiFile = f.getContainingFile();
        actions.add(ModCommand.psiUpdateStep(targetPsiFile,
          PropertiesBundle.message("add.missing.translation.quick.fix.name", localeName),
          (file, updater) -> addPropertyWithTemplate(file, updater, key, value)));
      }

      if (actions.isEmpty()) return ModCommand.nop();
      return ModCommand.chooseAction(PropertiesBundle.message("add.missing.translation.choose.title"), actions);
    }

    private static void addPropertyWithTemplate(@NotNull PsiFile file,
                                                @NotNull ModPsiUpdater updater,
                                                @NotNull String key,
                                                @NotNull String value) {
      PropertiesFile propsFile = PropertiesImplUtil.getPropertiesFile(file);
      if (propsFile == null) return;

      IProperty added = propsFile.addProperty(key, value, PropertyKeyValueFormat.PRESENTABLE);
      PsiElement addedPsi = added.getPsiElement();
      if (addedPsi instanceof PropertyImpl addedProperty) {
        ASTNode valueNode = addedProperty.getValueNode();
        if (valueNode != null) {
          updater.moveCaretTo(addedPsi);
          ModTemplateBuilder builder = updater.templateBuilder();
          builder.field(valueNode.getPsi(), value);
        }
        else {
          updater.moveCaretTo(addedPsi.getTextRange().getEndOffset());
        }
      }
    }
  }
}
