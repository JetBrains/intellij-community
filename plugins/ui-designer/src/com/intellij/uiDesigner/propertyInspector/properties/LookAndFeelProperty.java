// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.uiDesigner.propertyInspector.*;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author NecroRayder
 */
public final class LookAndFeelProperty extends Property<RadRootContainer, String> {

  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>() {
    @Override
    protected void customize(@NotNull final String value) {
      setText(value);
    }
  };

  private static class LookAndFeelEditor extends ComboBoxPropertyEditor<String> {

    LookAndFeelEditor() {
      myCbx.setRenderer(new ListCellRendererWrapper<String>() {
        @Override
        public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
          setText(value);
        }
      });
    }

    @Override
    public JComponent getComponent(RadComponent component, String value, InplaceContext inplaceContext) {
      myCbx.setModel(new DefaultComboBoxModel(LookAndFeelPropertyManager.getLookAndFeelNames()));
      myCbx.setSelectedItem(value);
      return myCbx;
    }

  }

  private final PropertyEditor<String> myEditor = new LookAndFeelEditor();

  public LookAndFeelProperty(final Project project) {
    super(null, "look and feel");
    if(!LookAndFeelPropertyManager.isStyleFileAvailable(project))
      LookAndFeelPropertyManager.createStyleFile(project);
    LookAndFeelPropertyManager.updateStylesFromFile();
  }

  public static LookAndFeel getDefaultLookAndFeel(){
    return LookAndFeelPropertyManager.getLookAndFeelFromClassName(UIManager.getSystemLookAndFeelClassName()).getLookAndFeel();
  }

  public static String getDefaultLookAndFeelName(){
    return LookAndFeelPropertyManager.getLookAndFeelFromClassName(UIManager.getSystemLookAndFeelClassName()).getLookAndFeel().getName();
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
    LookAndFeel laf = LookAndFeelPropertyManager.getLookAndFeelFromClassName(component.getLookAndFeel()).getLookAndFeel();
    if(laf != null) return laf.getName();
    return getDefaultLookAndFeelName();
  }

  @Override
  protected void setValueImpl(final RadRootContainer component, final String value) throws Exception {

    LookAndFeel laf = LookAndFeelPropertyManager.getLookAndFeelFromName(value).getLookAndFeel();
    Class lafClass = null;
    String lookAndFeel = getDefaultLookAndFeel().getClass().getName();

    if(laf != null)
       lafClass = laf.getClass();

    if(lafClass != null ) {

      lookAndFeel = lafClass.getName();

      if (lookAndFeel == null || lookAndFeel.length() == 0) {
        lookAndFeel = getDefaultLookAndFeel().getClass().getName();
      }

    }

    component.setOnlyLookAndFeel(lookAndFeel);

  }

}
