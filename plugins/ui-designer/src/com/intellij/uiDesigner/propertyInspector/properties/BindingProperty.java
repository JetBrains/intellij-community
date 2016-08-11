/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property<RadComponent, String> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.properties.BindingProperty");

  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>() {
    protected void customize(@NotNull final String value) {
      setText(value);
    }
  };
  private final BindingEditor myEditor;
  @NonNls private static final String PREFIX_HTML = "<html>";

  public BindingProperty(final Project project){
    super(null, "field name");
    myEditor = new BindingEditor(project);
  }

  public PropertyEditor<String> getEditor(){
    return myEditor;
  }

  @NotNull
  public PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  public String getValue(final RadComponent component){
    return component.getBinding();
  }

  protected void setValueImpl(final RadComponent component, final String value) throws Exception {
    if (Comparing.strEqual(value, component.getBinding(), true)) {
      return;
    }

    if (value.length() > 0 && !PsiNameHelper.getInstance(component.getProject()).isIdentifier(value)) {
      throw new Exception("Value '" + value + "' is not a valid identifier");
    }

    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    final String oldBinding = getValue(component);

    // Check that binding remains unique

    if (value.length() > 0) {
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
    if (newName.length() == 0) {
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
        MessageFormat.format(UIDesignerBundle.message("message.rename.field"), oldName, newName),
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

    final RenameProcessor processor = new RenameProcessor(project, oldField, newName, true, true);
    processor.run();
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

  @Nullable
  public static PsiField findBoundField(@NotNull final RadRootContainer root, final String fieldName) {
    final Project project = root.getProject();
    final String classToBind = root.getClassToBind();
    if (classToBind != null) {
      final PsiManager manager = PsiManager.getInstance(project);
      PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(classToBind, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        final PsiField oldBindingField = aClass.findFieldByName(fieldName, false);
        if (oldBindingField != null) {
          return oldBindingField;
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
    if (isFieldUnreferenced(oldBindingField)) {
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

  private static boolean isFieldUnreferenced(final PsiField field) {
    try {
      return ReferencesSearch.search(field).forEach(t -> {
        PsiFile f = t.getElement().getContainingFile();
        if (f != null && f.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
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

  @Nullable
  public static String suggestBindingFromText(final RadComponent component, String text) {
    if (StringUtil.startsWithIgnoreCase(text, PREFIX_HTML)) {
      text = Pattern.compile("<.+?>").matcher(text).replaceAll("");
    }
    ArrayList<String> words = new ArrayList<>(StringUtil.getWordsIn(text));
    if (words.size() > 0) {
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
