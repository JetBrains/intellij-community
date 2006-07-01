package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.ClassToBindRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ClassToBindProperty extends Property<RadRootContainer, String> {
  private final ClassToBindRenderer myRenderer;
  private final MyEditor myEditor;

  public ClassToBindProperty(final Project project) {
    super(null, "bind to class");
    myRenderer = new ClassToBindRenderer();
    myEditor = new MyEditor(project);
  }

  public PropertyEditor<String> getEditor(){
    return myEditor;
  }

  @NotNull
  public PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  public String getValue(final RadRootContainer component) {
    return component.getClassToBind();
  }

  protected void setValueImpl(final RadRootContainer component, final String value) throws Exception {
    String className = value;

    if (className != null && className.length() == 0) {
      className = null;
    }

    component.setClassToBind(className);
  }

  private final class MyEditor extends PropertyEditor<String> {
    private EditorTextField myEditorTextField;
    private Document myDocument;
    private final ComponentWithBrowseButton<EditorTextField> myTfWithButton;
    private String myInitialValue;
    private final Project myProject;
    private ClassToBindProperty.MyEditor.MyActionListener myActionListener;

    public MyEditor(final Project project) {
      myProject = project;
      myEditorTextField = new EditorTextField("", project, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
      myActionListener = new MyActionListener();
      myTfWithButton = new ComponentWithBrowseButton<EditorTextField>(myEditorTextField, myActionListener);
      myEditorTextField.setBorder(null);
      new MyCancelEditingAction().registerCustomShortcutSet(CommonShortcuts.ESCAPE, myTfWithButton);
      /*
      myEditorTextField.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            fireValueCommitted();
          }
        }
      );
      */
    }

    public String getValue() throws Exception {
      final String value = myDocument.getText();
      if (value.length() == 0 && myInitialValue == null) {
        return null;
      }
      return value.replace('$', '.'); // PSI works only with dots
    }

    public JComponent getComponent(final RadComponent component, final String value, final boolean inplace) {
      myInitialValue = value;
      setEditorText(value != null ? value : "");
      myActionListener.setModule(component.getModule());
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
      private Module myModule;

      public void setModule(final Module module) {
        myModule = module;
      }

      public void actionPerformed(final ActionEvent e){
        final String className = myEditorTextField.getText();
        final PsiClass aClass = FormEditingUtil.findClassToBind(myModule, className);

        final Project project = myModule.getProject();
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
          UIDesignerBundle.message("title.choose.class.to.bind"),
          GlobalSearchScope.projectScope(project),
          new TreeClassChooser.ClassFilter() { // we need show classes from the sources roots only
            public boolean isAccepted(final PsiClass aClass) {
              final VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
              return vFile != null && fileIndex.isInSource(vFile);
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