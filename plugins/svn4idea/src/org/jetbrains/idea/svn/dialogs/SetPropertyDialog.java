// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.Collection;
import java.util.TreeSet;

public class SetPropertyDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.dialogs.SetPropertyDialog");

  private final String myPropertyName;
  private final File[] myFiles;

  private JComboBox myPropertyNameBox;
  private JRadioButton mySetPropertyButton;
  private JTextArea myValueText;
  private JRadioButton myDeletePropertyButton;
  private JCheckBox myRecursiveButton;
  private final boolean myIsRecursionAllowed;
  private final SvnVcs myVCS;

  @NonNls private static final String HELP_ID = "vcs.subversion.property";
  private JPanel myMainPanel;

  public SetPropertyDialog(Project project, File[] files, String name, boolean allowRecursion) {
    super(project, true);
    myFiles = files;
    myPropertyName = name;
    myIsRecursionAllowed = allowRecursion;
    myVCS = SvnVcs.getInstance(project);
    setResizable(true);
    setTitle(SvnBundle.message("dialog.title.set.property"));
    getHelpAction().setEnabled(true);
    init();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }


  public JComponent getPreferredFocusedComponent() {
    return myPropertyNameBox;
  }

  public String getPropertyName() {
    return (String)myPropertyNameBox.getEditor().getItem();
  }

  public String getPropertyValue() {
    if (myDeletePropertyButton.isSelected()) {
      return null;
    }
    return myValueText.getText();
  }

  public boolean isRecursive() {
    return myRecursiveButton.isSelected();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  protected String getDimensionServiceKey() {
    return "svn.propertyDialog";
  }

  protected void init() {
    super.init();
    if (myPropertyName != null) {
      updatePropertyValue(myPropertyName);
    }
    else {
      myPropertyNameBox.getEditor().setItem("");
    }
    myPropertyNameBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        String name = getPropertyName();
        updatePropertyValue(name);
        getOKAction().setEnabled(name != null && !"".equals(name.trim()));
      }
    });
    Component editor = myPropertyNameBox.getEditor().getEditorComponent();
    if (editor instanceof JTextField) {
      JTextField jTextField = (JTextField)editor;
      jTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          String name = getPropertyName();
          updatePropertyValue(name);
          getOKAction().setEnabled(name != null && !"".equals(name.trim()));
        }
      });

    }
    getOKAction().setEnabled(myPropertyName != null && !"".equals(myPropertyName.trim()));
  }

  private void updatePropertyValue(String name) {
    if (myFiles.length == 0 || myFiles.length > 1) {
      return;
    }
    File file = myFiles[0];
    PropertyValue property = !StringUtil.isEmpty(name) ? getProperty(file, name) : null;

    if (property != null) {
      myValueText.setText(property.toString());
      myValueText.selectAll();
    }
    else {
      myValueText.setText("");
    }
  }

  @Nullable
  private PropertyValue getProperty(@NotNull File file, @NotNull String name) {
    PropertyValue result;

    try {
      PropertyClient client = myVCS.getFactory(file).createPropertyClient();
      result = client.getProperty(Target.on(file, Revision.WORKING), name, false, Revision.WORKING);
    }
    catch (SvnBindException e) {
      LOG.info(e);
      result = null;
    }

    return result;
  }

  protected JComponent createCenterPanel() {
    fillPropertyNames(myFiles);
    if (myPropertyName != null) {
      myPropertyNameBox.getEditor().setItem(myPropertyName);
      myPropertyNameBox.getEditor().selectAll();
    }
    mySetPropertyButton.addChangeListener(e -> {
      if (mySetPropertyButton.isSelected()) {
        myValueText.setEnabled(true);
      }
      else {
        myValueText.setEnabled(false);
      }
    });
    myRecursiveButton.setEnabled(myIsRecursionAllowed);
    return myMainPanel;
  }

  private void fillPropertyNames(File[] files) {
    final Collection<String> names = new TreeSet<>();
    if (files.length == 1) {
      File file = files[0];
      try {
        PropertyConsumer handler = new PropertyConsumer() {
          public void handleProperty(File path, PropertyData property) {
            String name = property.getName();
            if (name != null) {
              names.add(name);
            }
          }

          public void handleProperty(Url url, PropertyData property) {
          }

          public void handleProperty(long revision, PropertyData property) {
          }
        };

        PropertyClient client = myVCS.getFactory(file).createPropertyClient();
        client.list(Target.on(file, Revision.WORKING), Revision.WORKING, Depth.EMPTY, handler);
      }
      catch (SvnBindException e) {
        LOG.info(e);
      }
    }

    fillProperties(names);

    for (final String name : names) {
      myPropertyNameBox.addItem(name);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void fillProperties(final Collection<String> names) {
    names.add(SvnPropertyKeys.SVN_EOL_STYLE);
    names.add(SvnPropertyKeys.SVN_KEYWORDS);
    names.add(SvnPropertyKeys.SVN_NEEDS_LOCK);
    names.add(SvnPropertyKeys.SVN_MIME_TYPE);
    names.add(SvnPropertyKeys.SVN_EXECUTABLE);
    names.add(SvnPropertyKeys.SVN_IGNORE);
    names.add(SvnPropertyKeys.SVN_EXTERNALS);
  }
}
