// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BindingEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BindingProperty extends Property<RadComponent, String> {
  private static final Logger LOG = Logger.getInstance(BindingProperty.class);

  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<>() {
    @Override
    protected void customize(final @NotNull @NlsSafe String value) {
      setText(value);
    }
  };
  private final BindingEditor myEditor;
  private static final @NonNls String PREFIX_HTML = "<html>";

  public BindingProperty(final Project project){
    super(null, "field name");
    myEditor = new BindingEditor(project);
  }

  @Override
  public PropertyEditor<String> getEditor(){
    return myEditor;
  }

  @Override
  public @NotNull PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  @Override
  public String getValue(final RadComponent component){
    return component.getBinding();
  }

  @Override
  protected void setValueImpl(final RadComponent component, final String value) throws Exception {
    if (Comparing.strEqual(value, component.getBinding(), true)) {
      return;
    }

    if (!value.isEmpty() && !PsiNameHelper.getInstance(component.getProject()).isIdentifier(value)) {
      throw new Exception("Value '" + value + "' is not a valid identifier");
    }

    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    final String oldBinding = getValue(component);

    // Check that binding remains unique

    if (!value.isEmpty()) {
      if (!FormEditingUtil.isBindingUnique(component, value, root)) {
        throw new Exception(UIDesignerBundle.message("error.binding.not.unique"));
      }

      component.setBinding(value);
      component.setDefaultBinding(false);
    }
    else {
      if (component.isCustomCreateRequired()) {
        throw new Exception(UIDesignerBundle.message("error.custom.create.binding.required"));
      }
      component.setBinding(null);
      component.setCustomCreate(false);
    }

    // Set new value or rename old one. It means that previous binding exists
    // and the new one doesn't exist we need to ask user to create new field
    // or rename old one.

    updateBoundFieldName(root, oldBinding, value, component.getComponentClassName());
  }

  public static void updateBoundFieldName(final RadRootContainer root, final String oldName, final String newName, final String fieldClassName) {
    final String classToBind = root.getClassToBind();
    if (classToBind == null) return;

    final Project project = root.getProject();
    if (newName.isEmpty()) {
      checkRemoveUnusedField(root, oldName, FormEditingUtil.getNextSaveUndoGroupId(project));
      return;
    }

    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(classToBind, GlobalSearchScope.allScope(project));
    if(aClass == null){
      return;
    }

    if(oldName == null) {
      if (aClass.findFieldByName(newName, true) == null) {
        CreateFieldFix.runImpl(project, root, aClass, fieldClassName, newName, false,
                               FormEditingUtil.getNextSaveUndoGroupId(project));
      }
      return;
    }

    final PsiField oldField = aClass.findFieldByName(oldName, true);
    if(oldField == null){
      return;
    }

    if(aClass.findFieldByName(newName, true) != null) {
      checkRemoveUnusedField(root, oldName, FormEditingUtil.getNextSaveUndoGroupId(project));
      return;
    }

    // Show question to the user

    if (!isFieldUnreferenced(oldField)) {
      final int option = Messages.showYesNoDialog(project,
                                                  UIDesignerBundle.message("message.rename.field", oldName, newName),
                                                  UIDesignerBundle.message("title.rename"),
                                                  Messages.getQuestionIcon()
      );

      if(option != Messages.YES/*Yes*/){
        return;
      }
    }

    // Commit document before refactoring starts
    GuiEditor editor = DesignerToolWindowManager.getInstance(project).getActiveFormEditor();
    if (editor != null) {
      editor.refreshAndSave(false);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) {
      return;
    }

    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-337740")) {
      final RenameProcessor processor = new RenameProcessor(project, oldField, newName, true, true);
      processor.run();
    }
  }


  @Override
  public boolean isModified(final RadComponent component) {
    return component.getBinding() != null;
  }

  @Override
  public void resetValue(final RadComponent component) throws Exception {
    setValueImpl(component, "");
  }

  @Override
  public boolean appliesToSelection(final List<RadComponent> selection) {
    return selection.size() == 1;
  }

  public static @Nullable PsiField findBoundField(final @NotNull RadRootContainer root, final String fieldName) {
    final Project project = root.getProject();
    final String classToBind = root.getClassToBind();
    if (classToBind != null) {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-267358")) {
        final PsiManager manager = PsiManager.getInstance(project);
        PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(classToBind, GlobalSearchScope.allScope(project));
        if (aClass != null) {
          final PsiField oldBindingField = aClass.findFieldByName(fieldName, false);
          if (oldBindingField != null) {
            return oldBindingField;
          }
        }
      }
    }
    return null;
  }

  public static void checkRemoveUnusedField(final RadRootContainer rootContainer, final String fieldName, final Object undoGroupId) {
    final PsiField oldBindingField = findBoundField(rootContainer, fieldName);
    if (oldBindingField == null) {
      return;
    }
    final Project project = oldBindingField.getProject();
    final PsiClass aClass = oldBindingField.getContainingClass();
    Boolean result = checkFieldReferences(fieldName, oldBindingField, project);
    if (result == null) return;
    if (result) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(
        () -> CommandProcessor.getInstance().executeCommand(
          project,
          () -> {
            try {
              oldBindingField.delete();
            }
            catch (IncorrectOperationException e) {
              Messages.showErrorDialog(project, UIDesignerBundle.message("error.cannot.delete.unused.field", e.getMessage()),
                                       CommonBundle.getErrorTitle());
            }
          },
          UIDesignerBundle.message("command.delete.unused.field"), undoGroupId
        )
      );
    }
  }

  private static Boolean checkFieldReferences(String fieldName, PsiField oldBindingField, Project project) {
    Task.WithResult<Boolean, RuntimeException> task = new Task.WithResult<>(project, UIDesignerBundle.message("dialog.title.check.field.usages", fieldName), true) {
      @Override
      protected Boolean compute(@NotNull ProgressIndicator indicator) {
        return isFieldUnreferenced(oldBindingField);
      }
    };
    task.queue();
    Boolean result;
    try {
      result = task.getResult();
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    return result;
  }

  private static boolean isFieldUnreferenced(final PsiField field) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-722024")) {
      return ReferencesSearch.search(field).forEach(t -> {
        PsiFile f = t.getElement().getContainingFile();
        if (f != null && f.getFileType().equals(GuiFormFileType.INSTANCE)) {
          return true;
        }
        PsiMethod method = PsiTreeUtil.getParentOfType(t.getElement(), PsiMethod.class);
        if (method != null && method.getName().equals(AsmCodeGenerator.SETUP_METHOD_NAME)) {
          return true;
        }
        return false;
      });
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public static void checkCreateBindingFromText(final RadComponent component, final String text) {
    if (!component.isDefaultBinding()) {
      return;
    }
    RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(component);
    PsiField boundField = findBoundField(root, component.getBinding());
    if (boundField == null || !isFieldUnreferenced(boundField)) {
      return;
    }

    String binding = suggestBindingFromText(component, text);
    if (binding != null) {
      new BindingProperty(component.getProject()).setValueEx(component, binding);
      // keep the binding marked as default
      component.setDefaultBinding(true);
    }
  }

  public static @Nullable String suggestBindingFromText(final RadComponent component, String text) {
    if (StringUtil.startsWithIgnoreCase(text, PREFIX_HTML)) {
      text = Pattern.compile("<.+?>").matcher(text).replaceAll("");
    }
    ArrayList<String> words = new ArrayList<>(StringUtil.getWordsIn(text));
    if (!words.isEmpty()) {
      StringBuilder nameBuilder = new StringBuilder(StringUtil.decapitalize(words.get(0)));
      for(int i=1; i<words.size() && i < 4; i++) {
        nameBuilder.append(StringUtil.capitalize(words.get(i)));
      }
      final String shortClassName = StringUtil.capitalize(InsertComponentProcessor.getShortClassName(component.getComponentClassName()));
      if (shortClassName.equalsIgnoreCase(nameBuilder.toString())) {
        // avoid "buttonButton" case
        return null;
      }
      nameBuilder.append(shortClassName);

      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      Project project = root.getProject();
      String binding = JavaCodeStyleManager.getInstance(project).propertyNameToVariableName(nameBuilder.toString(), VariableKind.FIELD);
      if (FormEditingUtil.findComponentWithBinding(root, binding, component) != null) {
        binding = InsertComponentProcessor.getUniqueBinding(root, nameBuilder.toString());
      }
      return binding;
    }
    return null;
  }

  public static String getDefaultBinding(final RadComponent c) {
    RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(c);
    String binding = null;
    String text = FormInspectionUtil.getText(c.getModule(), c);
    if (text != null) {
      binding = suggestBindingFromText(c, text);
    }
    if (binding == null) {
      binding = InsertComponentProcessor.suggestBinding(root, c.getComponentClassName());
    }
    return binding;
  }
}
