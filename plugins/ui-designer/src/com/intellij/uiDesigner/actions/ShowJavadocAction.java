/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShowJavadocAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.ShowJavadocAction");

  public void actionPerformed(final AnActionEvent e) {
    final PropertyInspectorTable inspector = PropertyInspectorTable.DATA_KEY.getData(e.getDataContext());
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

  public void update(final AnActionEvent e) {
    final PropertyInspectorTable inspector = PropertyInspectorTable.DATA_KEY.getData(e.getDataContext());
    e.getPresentation().setEnabled(inspector != null &&
                                   inspector.getSelectedIntrospectedProperty() != null &&
                                   inspector.getComponentClass() != null);
  }
}
