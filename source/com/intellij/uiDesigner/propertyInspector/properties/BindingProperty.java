package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BindingEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BindingRenderer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property {
  private final GuiEditor myGuiEditor;

  private final BindingRenderer myRenderer;
  private final BindingEditor myEditor;

  public BindingProperty(final GuiEditor editor){
    super(null, "binding");
    myGuiEditor = editor;
    myRenderer = new BindingRenderer();
    myEditor = new BindingEditor(editor);
  }

  public Property[] getChildren(){
    return EMPTY_ARRAY;
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

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

    if (
      !GuiEditorUtil.isBindingUnique(component, newBinding, myGuiEditor.getRootContainer())
    ) {
      throw new Exception("binding is not unique");
    }

    // Set new value or rename old one. It means that previous binding exists
    // and the new one doesn't exist we need to ask user to create new field
    // or rename old one.

    final String oldBinding = (String)getValue(component);

    component.setBinding(newBinding);

    if(oldBinding == null){
      return;
    }

    final String classToBind = myGuiEditor.getRootContainer().getClassToBind();
    if(classToBind == null){
      return;
    }

    final Project project = myGuiEditor.getProject();
    final PsiClass aClass = PsiManager.getInstance(project).findClass(classToBind, GlobalSearchScope.allScope(project));
    if(aClass == null){
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
      myGuiEditor,
      "Do you want to rename field \"" + oldBinding + "\" to \"" + newBinding + "\" as well?",
      "Rename",
      Messages.getQuestionIcon()
    );

    if(option != 0/*Yes*/){
      return;
    }

    // Commit document before refactoring starts
    myGuiEditor.refreshAndSave(false);
    PsiDocumentManager.getInstance(myGuiEditor.getProject()).commitAllDocuments();

    final RenameProcessor processor = new RenameProcessor(project, oldField, newBinding, true, true, false);
    processor.run((Object)null);
  }
}
