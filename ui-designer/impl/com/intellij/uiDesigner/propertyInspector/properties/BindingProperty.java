package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.uiDesigner.*;
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
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property {
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

    final int option = Messages.showYesNoDialog(
      myProject,
      MessageFormat.format(UIDesignerBundle.message("message.rename.field"), oldBinding, newBinding),
      UIDesignerBundle.message("title.rename"),
      Messages.getQuestionIcon()
    );

    if(option != 0/*Yes*/){
      return;
    }

    // Commit document before refactoring starts
    GuiEditor editor = UIDesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
    if (editor != null) {
      editor.refreshAndSave(false);
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final RenameProcessor processor = new RenameProcessor(myProject, oldField, newBinding, true, true);
    processor.run();
  }
}
