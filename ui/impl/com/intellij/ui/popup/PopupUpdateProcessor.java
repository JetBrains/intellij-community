package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;

import java.awt.*;

/**
 * @author yole
 */
public class PopupUpdateProcessor extends JBPopupAdapter {
  private Condition<PsiElement> myPopupUpdater;

  public PopupUpdateProcessor(Condition<PsiElement> popupUpdater) {
    myPopupUpdater = popupUpdater;
  }

  public Condition<PsiElement> getUpdater() {
    return myPopupUpdater;
  }

  public void beforeShown(final Project project, final JBPopup jbPopup) {
    final Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null) {
      activeLookup.addLookupListener(new LookupAdapter() {
        public void currentItemChanged(LookupEvent event) {
          if (jbPopup.isVisible()) { //was not canceled yet
            final LookupItem item = event.getItem();
            if (item != null) {
              final Object o = item.getObject();
              if (o instanceof PsiElement) {
                jbPopup.cancel(); //close this one
                myPopupUpdater.value((PsiElement)o); //open next
              }
            }
          }
          activeLookup.removeLookupListener(this); //do not multiply listeners
        }
      });
    }
    else {
      final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
      boolean fromQuickSearch = focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
      if (fromQuickSearch) {
        ChooseByNameBase.JPanelProvider panelProvider = (ChooseByNameBase.JPanelProvider)focusedComponent.getParent();
        panelProvider.registerHint(jbPopup);
      }
    }
  }
}