package com.intellij.uiDesigner.actions;

import com.intellij.codeInsight.javadoc.JavaDocInfoComponent;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;
import java.awt.event.FocusEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShowJavadocAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.ShowJavadocAction");

  public void actionPerformed(final AnActionEvent e) {
    final PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    final IntrospectedProperty introspectedProperty = inspector.getSelectedIntrospectedProperty();
    final PsiClass aClass = inspector.getComponentClass();

    final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, introspectedProperty.getName(), false, true);
    LOG.assertTrue(getter != null);

    final PsiMethod setter = PropertyUtil.findPropertySetter(aClass, introspectedProperty.getName(), false, true);
    LOG.assertTrue(setter != null);

    final JavaDocManager javaDocManager = JavaDocManager.getInstance(aClass.getProject());

    final JavaDocInfoComponent component1 = new JavaDocInfoComponent(javaDocManager);
    final JavaDocInfoComponent component2 = new JavaDocInfoComponent(javaDocManager);

    final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();

    tabbedPane.addTab(UIDesignerBundle.message("tab.getter"), component1);
    tabbedPane.addTab(UIDesignerBundle.message("tab.setter"), component2);

    final LightweightHint hint = new LightweightHint(tabbedPane.getComponent()) {
      public void hide() {
        super.hide();
        inspector.requestFocusInWindow();
      }
    };

    component1.setHint(hint);
    component2.setHint(hint);

    javaDocManager.fetchDocInfo(javaDocManager.getDefaultProvider(getter), component1);
    javaDocManager.fetchDocInfo(javaDocManager.getDefaultProvider(setter), component2);

    final FocusWatcher focusWatcher = new FocusWatcher() {
      protected void focusLostImpl(final FocusEvent e) {
        hint.hide();
      }
    };

    focusWatcher.install(hint.getComponent());
    hint.show(inspector, 0, 0, inspector);
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          component1.requestFocus();
        }
      }
    );
  }

  public void update(final AnActionEvent e) {
    final PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    e.getPresentation().setEnabled(inspector != null &&
                                   inspector.getSelectedIntrospectedProperty() != null &&
                                   inspector.getComponentClass() != null);
  }
}
