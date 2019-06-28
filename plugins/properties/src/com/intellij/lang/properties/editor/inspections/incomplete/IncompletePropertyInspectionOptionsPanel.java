// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor.inspections.incomplete;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Locale;
import java.util.SortedSet;

/**
 * @author Dmitry Batkovich
 */
public class IncompletePropertyInspectionOptionsPanel {

  private final SortedSet<String> mySuffixes;
  private final JBList myList;

  public IncompletePropertyInspectionOptionsPanel(SortedSet<String> suffixes) {
    mySuffixes = suffixes;
    myList = new JBList(new MyListModel());
  }

  public JPanel buildPanel() {
    JPanel panel = ToolbarDecorator
      .createDecorator(myList)
      .setPanelBorder(IdeBorderFactory.createTitledBorder("Ignored suffixes"))
      .disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final String result = Messages.showInputDialog(CommonDataKeys.PROJECT.getData(button.getDataContext()),
                                                       "Suffixes to ignore (use comma to separate suffixes):",
                                                       "Add Ignored Suffixes", null);
        if (result != null) {
          mySuffixes.addAll(StringUtil.split(result, ","));
          changed();
        }
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        for (Object v : myList.getSelectedValues()) {
          mySuffixes.remove(v);
        }
        changed();
      }
    }).createPanel();
    myList.setCellRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, String suffix, int index, boolean selected, boolean hasFocus) {
        append(suffix);
        final Locale locale = PropertiesUtil.getLocale("_" + suffix + ".properties");
        if (locale != PropertiesUtil.DEFAULT_LOCALE) {
          if (PropertiesUtil.hasDefaultLanguage(locale)) {
            append(" ");
            append(PropertiesUtil.getPresentableLocale(locale), SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        } else {
          append("Default locale");
        }
      }
    });
    return panel;
  }

  public boolean showDialogAndGet(Project project) {
    return new DialogWrapper(project) {
      {
        init();
        setTitle("Locales to Ignore");
      }

      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        return buildPanel();
      }
    }.showAndGet();
  }

  private void changed() {
    ((MyListModel)myList.getModel()).modified();
  }

  private class MyListModel extends AbstractListModel {
    @Override
    public int getSize() {
      return mySuffixes.size();
    }

    @Override
    public Object getElementAt(int index) {
      return ArrayUtilRt.toStringArray(mySuffixes)[index];
    }

    public void modified() {
      fireContentsChanged(this, -1, -1);
    }
  }
}
