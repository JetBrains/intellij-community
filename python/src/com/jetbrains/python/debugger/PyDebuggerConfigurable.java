// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.function.Supplier;

public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;
  private JCheckBox mySupportGevent;
  private JBCheckBox mySupportQt;
  private JCheckBox myDropIntoDebuggerOnFailedTests;
  private JBLabel warningIcon;
  private ComboBox<PyQtBackend> myPyQtBackend;
  private ActionLink myActionLink;
  private JBTextField myAttachProcessFilter;
  private JBLabel myAttachFilterLabel;

  private enum PyQtBackend {
    AUTO(PyBundle.messagePointer("python.debugger.qt.backend.auto")),
    PYQT4("PyQt4"),
    PYQT5("PyQt5"),
    PYSIDE("PySide"),
    PYSIDE2("PySide2");

    PyQtBackend(@NlsSafe @NotNull String displayName) {
      myDisplayNameSupplier = () -> displayName;
    }

    PyQtBackend(Supplier<@Nls String> displayNamePointer) {
      myDisplayNameSupplier = displayNamePointer;
    }

    @Override
    public @Nls String toString() {
      return myDisplayNameSupplier.get();
    }

    private final Supplier<@Nls String> myDisplayNameSupplier;
  }

  private final Project myProject;

  public PyDebuggerConfigurable(Project project) {
    myProject = project;
    Arrays.stream(PyQtBackend.values()).forEach(e -> myPyQtBackend.addItem(e));

    mySupportQt.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myPyQtBackend.setEnabled(mySupportQt.isSelected());
      }
    });

    myAttachFilterLabel.setText(PyBundle.message("debugger.attach.to.process.filter.names"));
  }

  @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.PyDebuggerConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger.python";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    return myAttachToSubprocess.isSelected() != settings.isAttachToSubprocess() ||
           mySaveSignatures.isSelected() != settings.isSaveCallSignatures() ||
           mySupportGevent.isSelected() != settings.isSupportGeventDebugging() ||
           myDropIntoDebuggerOnFailedTests.isSelected() != settings.isDropIntoDebuggerOnFailedTest() ||
           mySupportQt.isSelected() != settings.isSupportQtDebugging() ||
           (myPyQtBackend.getSelectedItem() != null &&
            !StringUtil.toLowerCase((((PyQtBackend)myPyQtBackend.getSelectedItem()).name())).equals(settings.getPyQtBackend())) ||
           !myAttachProcessFilter.getText().equals(settings.getAttachProcessFilter());
  }

  @Override
  public void apply() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    settings.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    settings.setSaveCallSignatures(mySaveSignatures.isSelected());
    settings.setSupportGeventDebugging(mySupportGevent.isSelected());
    settings.setDropIntoDebuggerOnFailedTest(myDropIntoDebuggerOnFailedTests.isSelected());
    settings.setSupportQtDebugging(mySupportQt.isSelected());

    Object selectedBackend = myPyQtBackend.getSelectedItem();
    if (selectedBackend != null) {
      settings.setPyQtBackend(StringUtil.toLowerCase(((PyQtBackend)selectedBackend).name()));
    }

    settings.setAttachProcessFilter(myAttachProcessFilter.getText());
  }

  @Override
  public void reset() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    myAttachToSubprocess.setSelected(settings.isAttachToSubprocess());
    mySaveSignatures.setSelected(settings.isSaveCallSignatures());
    mySupportGevent.setSelected(settings.isSupportGeventDebugging());
    myDropIntoDebuggerOnFailedTests.setSelected(settings.isDropIntoDebuggerOnFailedTest());
    mySupportQt.setSelected(settings.isSupportQtDebugging());
    myPyQtBackend.setSelectedItem(PyQtBackend.valueOf(StringUtil.toUpperCase(settings.getPyQtBackend())));
    myAttachProcessFilter.setText(settings.getAttachProcessFilter());
  }

  private void createUIComponents() {
    warningIcon = new JBLabel(AllIcons.General.BalloonWarning);
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      new TooltipWithClickableLinks.ForBrowser(warningIcon,
                                               PyBundle.message("debugger.warning.message")));

    myActionLink = new ActionLink(PyBundle.message("form.debugger.clear.caches.action"), e -> {
        boolean cleared = PySignatureCacheManager.getInstance(myProject).clearCache();
        String message;
        if (cleared) {
          message = PyBundle.message("python.debugger.collection.signatures.deleted");
        }
        else {
          message = PyBundle.message("python.debugger.nothing.to.delete");
        }
        Messages.showInfoMessage(myProject, message, PyBundle.message("debugger.delete.signature.cache"));
    });
  }
}
