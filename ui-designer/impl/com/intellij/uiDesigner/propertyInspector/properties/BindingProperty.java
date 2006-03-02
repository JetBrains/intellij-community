package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.editors.BindingEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BindingRenderer;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.util.Query;
import com.intellij.util.Processor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property<RadComponent> {
  private final Project myProject;

  private final BindingRenderer myRenderer;
  private final BindingEditor myEditor;

  public BindingProperty(final Project project){
    super(null, "binding");
    myProject = project;
    myRenderer = new BindingRenderer();
    myEditor = new BindingEditor(project);
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

  @NotNull
  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public Object getValue(final RadComponent component){
    return component.getBinding();
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    final String newBinding = (String)value;

    if (newBinding.length() == 0) {
      checkRemoveUnusedField(myProject, component);
      component.setBinding(null);
      return;
    }

    //TODO[anton,vova]: check identifier!!!

    // Check that binding remains unique

    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    if (
      !GuiEditorUtil.isBindingUnique(component, newBinding, root)
    ) {
      //noinspection HardCodedStringLiteral
      throw new Exception("binding is not unique");
    }

    // Set new value or rename old one. It means that previous binding exists
    // and the new one doesn't exist we need to ask user to create new field
    // or rename old one.

    final String oldBinding = (String)getValue(component);

    component.setBinding(newBinding);

    final String classToBind = root.getClassToBind();
    if(classToBind == null){
      return;
    }

    final PsiClass aClass = PsiManager.getInstance(myProject).findClass(classToBind, GlobalSearchScope.allScope(myProject));
    if(aClass == null){
      return;
    }

    if(oldBinding == null) {
      if (aClass.findFieldByName(newBinding, true) == null) {
        CreateFieldFix.runImpl(myProject, root, aClass, component.getComponentClassName(), newBinding, false);
      }
      return;
    }

    final PsiField oldField = aClass.findFieldByName(oldBinding, true);
    if(oldField == null){
      return;
    }

    if(aClass.findFieldByName(newBinding, true) != null){
      return;
    }

    // Show question to the user

    if (!isFieldUnreferenced(oldField)) {
      final int option = Messages.showYesNoDialog(
        myProject,
        MessageFormat.format(UIDesignerBundle.message("message.rename.field"), oldBinding, newBinding),
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

    final RenameProcessor processor = new RenameProcessor(myProject, oldField, newBinding, true, true);
    processor.run();
  }

  public static void checkRemoveUnusedField(final Project project, final RadComponent component) {
    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    final String classToBind = root.getClassToBind();
    if (classToBind != null) {
      final PsiManager manager = PsiManager.getInstance(project);
      PsiClass aClass = manager.findClass(classToBind, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        final PsiField oldBindingField = aClass.findFieldByName(component.getBinding(), false);
        if (oldBindingField != null) {
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
      }
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
}
