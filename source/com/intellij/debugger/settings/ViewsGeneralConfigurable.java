package com.intellij.debugger.settings;

import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class ViewsGeneralConfigurable implements Configurable {
  private JPanel myPanel;
  private JCheckBox myAutoscrollCheckBox;
  private JCheckBox myShowSyntheticsCheckBox;

  private JCheckBox mySortCheckBox;
  private JCheckBox myHideNullElementsCheckBox;
  private JCheckBox myShowStaticCheckBox;
  private StateRestoringCheckBox myShowStaticFinalCheckBox;
  private JPanel myArrayConfigurablePlace;
  private ArrayRendererConfigurable myArrayRendererConfigurable;
  private JCheckBox myAlternativeViews;

  public ViewsGeneralConfigurable() {
    myShowStaticCheckBox.addChangeListener(new ChangeListener(){
      public void stateChanged(ChangeEvent e) {
        if(myShowStaticCheckBox.isSelected()) {
          myShowStaticFinalCheckBox.makeSelectable();
        } else {
          myShowStaticFinalCheckBox.makeUnselectable(false);
        }
      }
    });

    myArrayRendererConfigurable = new ArrayRendererConfigurable(NodeRendererSettings.getInstance().getArrayRenderer());
    myArrayConfigurablePlace.setLayout(new BorderLayout());
    myArrayConfigurablePlace.add(myArrayRendererConfigurable.createComponent());
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getDisplayName() {
    return "Data views";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() {
    if (myPanel != null) {
      final boolean renderersWereModified = areDefaultRenderersModified();
      
      final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
      generalSettings.AUTOSCROLL_TO_NEW_LOCALS  = myAutoscrollCheckBox.isSelected();
      generalSettings.USE_ALTERNATIVE_RENDERERS = myAlternativeViews.isSelected();
      generalSettings.HIDE_NULL_ARRAY_ELEMENTS  = myHideNullElementsCheckBox.isSelected();

      final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
      classRenderer.SORT_ASCENDING = mySortCheckBox.isSelected();
      classRenderer.SHOW_STATIC = myShowStaticCheckBox.isSelected();
      classRenderer.SHOW_STATIC_FINAL = myShowStaticFinalCheckBox.isSelectedWhenSelectable();
      classRenderer.SHOW_SYNTHETICS = myShowSyntheticsCheckBox.isSelected();

      myArrayRendererConfigurable.apply();

      if (renderersWereModified) {
        NodeRendererSettings.getInstance().fireRenderersChanged();
      }
    }
  }

  public void reset() {
    ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();

    myAutoscrollCheckBox.setSelected(generalSettings.AUTOSCROLL_TO_NEW_LOCALS);
    myHideNullElementsCheckBox.setSelected(generalSettings.HIDE_NULL_ARRAY_ELEMENTS);
    myAlternativeViews.setSelected(generalSettings.USE_ALTERNATIVE_RENDERERS);

    ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();

    myShowSyntheticsCheckBox.setSelected(classRenderer.SHOW_SYNTHETICS);
    mySortCheckBox.setSelected(classRenderer.SORT_ASCENDING);
    myShowStaticCheckBox.setSelected(classRenderer.SHOW_STATIC);
    myShowStaticFinalCheckBox.setSelected(classRenderer.SHOW_STATIC_FINAL);
    if(!classRenderer.SHOW_STATIC) {
      myShowStaticFinalCheckBox.makeUnselectable(false);
    }

    myArrayRendererConfigurable.reset();
  }

  public boolean isModified() {
  return areGeneralSettingsModified() || areDefaultRenderersModified();
  }

  private boolean areGeneralSettingsModified() {
    ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    return
    (generalSettings.AUTOSCROLL_TO_NEW_LOCALS  != myAutoscrollCheckBox.isSelected()) ||
    (generalSettings.USE_ALTERNATIVE_RENDERERS != myAlternativeViews.isSelected()) ||
    (generalSettings.HIDE_NULL_ARRAY_ELEMENTS  != myHideNullElementsCheckBox.isSelected());
  }

  private boolean areDefaultRenderersModified() {
    if (myArrayRendererConfigurable.isModified()) {
      return true;
    }
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    return
    (classRenderer.SORT_ASCENDING != mySortCheckBox.isSelected()) ||
    (classRenderer.SHOW_STATIC != myShowStaticCheckBox.isSelected()) ||
    (classRenderer.SHOW_STATIC_FINAL != myShowStaticFinalCheckBox.isSelectedWhenSelectable()) ||
    (classRenderer.SHOW_SYNTHETICS != myShowSyntheticsCheckBox.isSelected());
  }

  public String getHelpTopic() {
    return null;
  }
}