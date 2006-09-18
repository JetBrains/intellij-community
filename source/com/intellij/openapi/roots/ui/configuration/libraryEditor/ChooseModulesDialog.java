/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 14-Aug-2006
 * Time: 12:13:18
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChooseModulesDialog extends DialogWrapper {
  protected ElementsChooser<Module> myChooser;
  private String myDescription;

  public ChooseModulesDialog(Component parent, final List<Module> items, final String title) {
    super(parent, true);
    initializeDialog(items, title);
  }

  public ChooseModulesDialog(final Project project, final List<Module> items, final String title, final String description) {
    super(project, true);
    myDescription = description;
    initializeDialog(items, title);
  }

  private void initializeDialog(final List<Module> items, final String title) {
    setTitle(title);
    myChooser = new ElementsChooser<Module>(false) {
      protected String getItemText(final Module item) {
        return item.getName();
      }
    };
    myChooser.setColorUnmarkedElements(false);

    setElements(items, items.size() > 0 ? items.subList(0, 1) : Collections.<Module>emptyList());
    myChooser.getComponent().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    myChooser.getComponent().addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isPopupTrigger() && !e.isConsumed()) {
          e.consume();
          doOKAction();
        }
      }
    });
    init();
  }

  public List<Module> getChosenElements() {
    return myChooser.getSelectedElements();
  }

  public JComponent getPreferredFocusedComponent() {
    return myChooser.getComponent();
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myChooser.getComponent()), BorderLayout.CENTER);
    if (myDescription != null) {
      panel.add(new JLabel(myDescription), BorderLayout.NORTH);
    }
    return panel;
  }

  private void setElements(final Collection<Module> elements, final Collection<Module> elementsToSelect) {
    myChooser.clear();
    for (final Module item : elements) {
      myChooser.addElement(item, false, createElementProperties(item));
    }
    myChooser.selectElements(elementsToSelect);
  }

  private static ElementsChooser.ElementProperties createElementProperties(final Module item) {
    return new ElementsChooser.ElementProperties() {
      public Icon getIcon() {
        return IconUtilEx.getIcon(item, 0);
      }

      public Color getColor() {
        return null;
      }
    };
  }

}