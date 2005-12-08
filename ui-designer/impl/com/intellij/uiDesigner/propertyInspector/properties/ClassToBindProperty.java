package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.ClassToBindRenderer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ClassToBindProperty extends Property {
  private final ClassToBindRenderer myRenderer;
  private final MyEditor myEditor;
  private final GuiEditor myUiEditor;

  public ClassToBindProperty(final GuiEditor editor){
    super(null, "bind to class");
    myUiEditor = editor;
    myRenderer = new ClassToBindRenderer();
    myEditor = new MyEditor(editor.getProject());
  }

  @NotNull
  public Property[] getChildren(){
    return EMPTY_ARRAY;
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

  @NotNull
  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public Object getValue(final RadComponent component){
    return ((RadRootContainer)component).getClassToBind();
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    String className = (String)value;

    if (className != null && className.length() == 0) {
      className = null;
    }

    ((RadRootContainer)component).setClassToBind(className);
  }

  private final class MyEditor extends PropertyEditor{
    private EditorTextField myEditorTextField;
    private Document myDocument;
    private final ComponentWithBrowseButton<EditorTextField> myTfWithButton;
    private String myInitialValue;
    private final Project myProject;

    public MyEditor(final Project project) {
      myProject = project;
      myEditorTextField = new EditorTextField("", project, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
      myTfWithButton = new ComponentWithBrowseButton<EditorTextField>(myEditorTextField, new MyActionListener());
      myEditorTextField.setBorder(null);
      new MyCancelEditingAction().registerCustomShortcutSet(CommonShortcuts.ESCAPE, myTfWithButton);
      /*
      myEditorTextField.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            fireValueCommited();
          }
        }
      );
      */
    }

    public Object getValue() throws Exception {
      final String value = myDocument.getText();
      if (value.length() == 0 && myInitialValue == null) {
        return null;
      }
      return value.replace('$', '.'); // PSI works only with dots
    }

    public JComponent getComponent(final RadComponent component, final Object value, final boolean inplace) {
      final String s = (String)value;
      myInitialValue = s;
      setEditorText(s != null ? s : "");
      return myTfWithButton;
    }

    private void setEditorText(final String s) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      final PsiElementFactory factory = manager.getElementFactory();
      PsiPackage defaultPackage = manager.findPackage("");
      final PsiCodeFragment fragment = factory.createReferenceCodeFragment(s, defaultPackage, true, true);
      myDocument = PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
      myEditorTextField.setDocument(myDocument);
    }

    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myTfWithButton);
    }

    private final class MyActionListener implements ActionListener{
      public void actionPerformed(final ActionEvent e){
        final String className = myEditorTextField.getText();
        final PsiClass aClass = FormEditingUtil.findClassToBind(myUiEditor.getModule(), className);

        final Project project = myUiEditor.getProject();
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createNoInnerClassesScopeChooser(
          UIDesignerBundle.message("title.choose.class.to.bind"),
          GlobalSearchScope.projectScope(project),
          new TreeClassChooser.ClassFilter() { // we need show classes from the sources roots only
            public boolean isAccepted(final PsiClass aClass) {
              final VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
              return fileIndex.isInSource(vFile);
            }
          },
          aClass
        );
        chooser.showDialog();

        final PsiClass result = chooser.getSelectedClass();
        if (result != null) {
          setEditorText(result.getQualifiedName());
        }

        myEditorTextField.requestFocus(); // todo[anton] make it via providing proper parent
      }
    }

    private final class MyCancelEditingAction extends AnAction{
      public void actionPerformed(final AnActionEvent e) {
        fireEditingCancelled();
      }
    }
  }
}