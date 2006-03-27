package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.editors.BindingEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property<RadComponent, String> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.properties.BindingProperty");

  private final Project myProject;

  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>() {
    protected void customize(final String value) {
      setText(value);
    }
  };
  private final BindingEditor myEditor;

  public BindingProperty(final Project project){
    super(null, "binding");
    myProject = project;
    myEditor = new BindingEditor(project);
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

  @NotNull
  public PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  public String getValue(final RadComponent component){
    return component.getBinding();
  }

  protected void setValueImpl(final RadComponent component, final String value) throws Exception{
    if (value.length() == 0) {
      checkRemoveUnusedField(component);
      component.setBinding(null);
      return;
    }

    //TODO[anton,vova]: check identifier!!!

    // Check that binding remains unique

    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    if (
      !GuiEditorUtil.isBindingUnique(component, value, root)
    ) {
      //noinspection HardCodedStringLiteral
      throw new Exception("binding is not unique");
    }

    // Set new value or rename old one. It means that previous binding exists
    // and the new one doesn't exist we need to ask user to create new field
    // or rename old one.

    final String oldBinding = getValue(component);

    component.setBinding(value);
    component.setDefaultBinding(false);

    final String classToBind = root.getClassToBind();
    if(classToBind == null){
      return;
    }

    final PsiClass aClass = PsiManager.getInstance(myProject).findClass(classToBind, GlobalSearchScope.allScope(myProject));
    if(aClass == null){
      return;
    }

    if(oldBinding == null) {
      if (aClass.findFieldByName(value, true) == null) {
        CreateFieldFix.runImpl(myProject, root, aClass, component.getComponentClassName(), value, false);
      }
      return;
    }

    final PsiField oldField = aClass.findFieldByName(oldBinding, true);
    if(oldField == null){
      return;
    }

    if(aClass.findFieldByName(value, true) != null){
      return;
    }

    // Show question to the user

    if (!isFieldUnreferenced(oldField)) {
      final int option = Messages.showYesNoDialog(
        myProject,
        MessageFormat.format(UIDesignerBundle.message("message.rename.field"), oldBinding, value),
        UIDesignerBundle.message("title.rename"),
        Messages.getQuestionIcon()
      );

      if(option != 0/*Yes*/){
        return;
      }
    }

    // Commit document before refactoring starts
    GuiEditor editor = UIDesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
    if (editor != null) {
      editor.refreshAndSave(false);
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, aClass)) {
      return;
    }

    final RenameProcessor processor = new RenameProcessor(myProject, oldField, value, true, true);
    processor.run();
  }

  @Nullable
  public static PsiField findBoundField(final RadComponent component) {
    final Project project = component.getModule().getProject();
    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    final String classToBind = root.getClassToBind();
    if (classToBind != null) {
      final PsiManager manager = PsiManager.getInstance(project);
      PsiClass aClass = manager.findClass(classToBind, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        final PsiField oldBindingField = aClass.findFieldByName(component.getBinding(), false);
        if (oldBindingField != null) {
          return oldBindingField;
        }
      }
    }
    return null;
  }

  public static void checkRemoveUnusedField(final RadComponent component) {
    final PsiField oldBindingField = findBoundField(component);
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
        new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(
              project,
              new Runnable() {
                public void run() {
                  try {
                    oldBindingField.delete();
                  }
                  catch (IncorrectOperationException e) {
                    Messages.showErrorDialog(project, UIDesignerBundle.message("error.cannot.delete.unused.field", e.getMessage()),
                                             CommonBundle.getErrorTitle());
                  }
                }
              },
              UIDesignerBundle.message("command.delete.unused.field"), null
            );
          }
        }
      );
    }
  }

  private static boolean isFieldUnreferenced(final PsiField field) {
    final Query<PsiReference> query = ReferencesSearch.search(field);
    return query.forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference t) {
        PsiMethod method = PsiTreeUtil.getParentOfType(t.getElement(), PsiMethod.class);
        if (method != null && method.getName().equals(AsmCodeGenerator.SETUP_METHOD_NAME)) {
          return true;
        }
        return false;
      }
    });
  }

  public static void checkCreateBindingFromText(final RadComponent component, final String text) {
    if (!component.isDefaultBinding()) {
      return;
    }
    PsiField boundField = findBoundField(component);
    if (boundField == null || !isFieldUnreferenced(boundField)) {
      return;
    }

    String binding = suggestBindingFromText(component, text);
    if (binding != null) {
      try {
        new BindingProperty(component.getProject()).setValue(component, binding);
        // keep the binding marked as default
        component.setDefaultBinding(true);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public static String suggestBindingFromText(final RadComponent component, final String text) {
    List<String> words = StringUtil.getWordsIn(text);
    if (words.size() > 0) {
      StringBuilder nameBuilder = new StringBuilder(StringUtil.decapitalize(words.get(0)));
      for(int i=1; i<words.size(); i++) {
        nameBuilder.append(StringUtil.capitalize(words.get(i)));
      }
      nameBuilder.append(StringUtil.capitalize(InsertComponentProcessor.getShortClassName(component.getComponentClassName())));

      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      Project project = root.getModule().getProject();
      String binding = CodeStyleManager.getInstance(project).propertyNameToVariableName(nameBuilder.toString(), VariableKind.FIELD);
      if (FormEditingUtil.findComponentWithBinding(root, binding, component) != null) {
        binding = InsertComponentProcessor.getUniqueBinding(root, nameBuilder.toString());
      }
      return binding;
    }
    return null;
  }
}
