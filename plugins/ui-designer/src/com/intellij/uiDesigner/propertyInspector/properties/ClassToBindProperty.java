// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.ide.util.ClassFilter;
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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.ClassToBindRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

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

  @Override
  public PropertyEditor<String> getEditor(){
    return myEditor;
  }

  @Override
  @NotNull
  public PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  @Override
  public String getValue(final RadRootContainer component) {
    return component.getClassToBind();
  }

  @Override
  protected void setValueImpl(final RadRootContainer component, final String value) {
    String className = value;

    if (className != null && className.length() == 0) {
      className = null;
    }

    component.setClassToBind(className);
  }

  private static final class MyEditor extends PropertyEditor<String> {
    private final EditorTextField myEditorTextField;
    private Document myDocument;
    private final ComponentWithBrowseButton<EditorTextField> myTfWithButton;
    private String myInitialValue;
    private final Project myProject;
    private final ClassToBindProperty.MyEditor.MyActionListener myActionListener;

    MyEditor(final Project project) {
      myProject = project;
      myEditorTextField = new EditorTextField("", project, StdFileTypes.JAVA) {
        @Override
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
      myActionListener = new MyActionListener();
      myTfWithButton = new ComponentWithBrowseButton<>(myEditorTextField, myActionListener);
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

    @Override
    public String getValue() {
      final String value = myDocument.getText();
      if (value.length() == 0 && myInitialValue == null) {
        return null;
      }
      return value.replace('$', '.'); // PSI works only with dots
    }

    @Override
    public JComponent getComponent(final RadComponent component, final String value, final InplaceContext inplaceContext) {
      myInitialValue = value;
      setEditorText(value != null ? value : "");
      myActionListener.setComponent(component);
      return myTfWithButton;
    }

    private void setEditorText(final String s) {
      final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
      PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
      final PsiCodeFragment fragment = factory.createReferenceCodeFragment(s, defaultPackage, true, true);
      myDocument = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
      myEditorTextField.setDocument(myDocument);
    }

    @Override
    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myTfWithButton);
    }

    private final class MyActionListener implements ActionListener{
      RadComponent myComponent;

      public void setComponent(RadComponent component) {
        myComponent = component;
      }

      @Override
      public void actionPerformed(final ActionEvent e){
        final String className = myEditorTextField.getText();
        final PsiClass aClass = FormEditingUtil.findClassToBind(myComponent.getModule(), className);

        final Project project = myComponent.getProject();
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
          UIDesignerBundle.message("title.choose.class.to.bind"),
          GlobalSearchScope.projectScope(project),
          new ClassFilter() { // we need show classes from the sources roots only
            @Override
            public boolean isAccepted(final PsiClass aClass) {
              final VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
              return vFile != null && fileIndex.isUnderSourceRootOfType(vFile, JavaModuleSourceRootTypes.SOURCES);
            }
          },
          aClass
        );
        chooser.showDialog();

        final PsiClass result = chooser.getSelected();
        if (result != null) {
          setEditorText(result.getQualifiedName());
        }

        // todo[anton] make it via providing proper parent
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditorTextField, true));
      }
    }

    private final class MyCancelEditingAction extends AnAction{
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        fireEditingCancelled();
      }
    }
  }
}
