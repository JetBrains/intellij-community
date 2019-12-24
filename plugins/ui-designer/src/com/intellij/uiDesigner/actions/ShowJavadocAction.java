// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShowJavadocAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(ShowJavadocAction.class);

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
    final IntrospectedProperty introspectedProperty = inspector.getSelectedIntrospectedProperty();
    final PsiClass aClass = inspector.getComponentClass();

    final PsiMethod getter = PropertyUtilBase.findPropertyGetter(aClass, introspectedProperty.getName(), false, true);
    LOG.assertTrue(getter != null);

    final PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, introspectedProperty.getName(), false, true);
    LOG.assertTrue(setter != null);

    final DocumentationManager documentationManager = DocumentationManager.getInstance(aClass.getProject());

    final DocumentationComponent component1 = new DocumentationComponent(documentationManager);
    final DocumentationComponent component2 = new DocumentationComponent(documentationManager);

    final Disposable disposable = Disposer.newDisposable();
    final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(disposable);

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
      hint.show(new RelativePoint(inspector, new Point(0,0)));
      //component1.requestFocus();
    });
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
    e.getPresentation().setEnabled(inspector != null &&
                                   inspector.getSelectedIntrospectedProperty() != null &&
                                   inspector.getComponentClass() != null);
  }
}
