// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class ShowJavadocAction extends AnAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
    GuiEditor guiEditor = e.getData(GuiEditor.DATA_KEY);
    if (inspector == null || guiEditor == null) return;
    IntrospectedProperty<?> introspectedProperty = inspector.getSelectedIntrospectedProperty();
    String radComponentClassName = inspector.getSelectedRadComponentClassName();
    if (introspectedProperty == null || radComponentClassName == null) return;
    Module module = guiEditor.getModule();
    PsiClass aClass = JavaPsiFacade.getInstance(module.getProject()).findClass(
      radComponentClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    if (aClass == null) return;

    PsiMethod getter = PropertyUtilBase.findPropertyGetter(aClass, introspectedProperty.getName(), false, true);
    PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, introspectedProperty.getName(), false, true);
    if (getter == null || setter == null) return;

    DocumentationManager documentationManager = DocumentationManager.getInstance(aClass.getProject());

    DocumentationComponent component1 = new DocumentationComponent(documentationManager);
    DocumentationComponent component2 = new DocumentationComponent(documentationManager);

    Disposable disposable = Disposer.newDisposable();
    TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(disposable);

    tabbedPane.addTab(UIDesignerBundle.message("tab.getter"), component1);
    tabbedPane.addTab(UIDesignerBundle.message("tab.setter"), component2);

    documentationManager.fetchDocInfo(getter, component1);
    documentationManager.queueFetchDocInfo(setter, component2).doWhenProcessed(() -> {
      final JBPopup hint =
        JBPopupFactory.getInstance().createComponentPopupBuilder(tabbedPane.getComponent(), component1)
          .setDimensionServiceKey(aClass.getProject(), DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
          .setResizable(true)
          .setMovable(true)
          .setRequestFocus(true)
          .setTitle(UIDesignerBundle.message("property.javadoc.title", introspectedProperty.getName()))
          .createPopup();
      component1.setHint(hint);
      component2.setHint(hint);
      Disposer.register(hint, component1);
      Disposer.register(hint, component2);
      Disposer.register(hint, disposable);
      hint.show(new RelativePoint(inspector, new Point(0, 0)));
      //component1.requestFocus();
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
    e.getPresentation().setEnabled(inspector != null &&
                                   inspector.getSelectedIntrospectedProperty() != null &&
                                   inspector.getSelectedRadComponentClassName() != null);
  }
}
