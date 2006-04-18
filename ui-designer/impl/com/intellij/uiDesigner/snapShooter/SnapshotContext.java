/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;

import javax.swing.*;
import java.awt.Container;
import java.util.*;

/**
 * @author yole
 */
public class SnapshotContext {
  private Palette myPalette;
  private RadRootContainer myRootContainer;
  private Set<ButtonGroup> myButtonGroups = new HashSet<ButtonGroup>();
  private Map<JComponent, RadComponent> myImportMap = new HashMap<JComponent, RadComponent>();

  public SnapshotContext() {
    myPalette = new Palette(null);
    myRootContainer = new RadRootContainer(null, "1");
  }

  public RadRootContainer getRootContainer() {
    return myRootContainer;
  }

  public Palette getPalette() {
    return myPalette;
  }

  public String newId() {
    return FormEditingUtil.generateId(myRootContainer);
  }

  public void registerComponent(final JComponent component, final RadComponent radComponent) {
    myImportMap.put(component, radComponent);
  }

  public void registerButtonGroup(final ButtonGroup group) {
    myButtonGroups.add(group);
  }

  public void processButtonGroups() {
    for(ButtonGroup group: myButtonGroups) {
      RadButtonGroup radButtonGroup = myRootContainer.createGroup(myRootContainer.suggestGroupName());
      Enumeration<AbstractButton> elements = group.getElements();
      while(elements.hasMoreElements()) {
        AbstractButton btn = elements.nextElement();
        RadComponent c = myImportMap.get(btn);
        if (c != null) {
          radButtonGroup.add(c);
        }
      }
    }
  }

  public void notifyUnknownLayoutManager(Container container) {
    int choice = JOptionPane.showOptionDialog(
      null,
      UIDesignerBundle.message("snapshot.unknown.layout.manager", container.getLayout().getClass().getName()),
      UIDesignerBundle.message("snapshot.title"),
      JOptionPane.DEFAULT_OPTION,
      JOptionPane.QUESTION_MESSAGE,
      Messages.getQuestionIcon(),
      new String[] {
        UIDesignerBundle.message("snapshot.button.ignore"),
        CommonBundle.getCancelButtonText()
      },
      UIDesignerBundle.message("snapshot.button.ignore")
    );
    if (choice != 0) {
      throw new CancelSnapshotException();
    }
  }
}
