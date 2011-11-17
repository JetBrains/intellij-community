/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Collection;
import java.util.TreeSet;

/**
 * @author alex
 */
public class SetPropertyDialog extends DialogWrapper {
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
    myPropertyNameBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          String name = getPropertyName();
          updatePropertyValue(name);
          getOKAction().setEnabled(name != null && !"".equals(name.trim()));
        }
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
    SVNPropertyData property;
    try {
      SVNWCClient client = myVCS.createWCClient();
      property = client.doGetProperty(file, name, SVNRevision.WORKING, SVNRevision.WORKING);
    }
    catch (SVNException e) {
      property = null;
    }
    if (property != null) {
      myValueText.setText(SVNPropertyValue.getPropertyAsString(property.getValue()));
      myValueText.selectAll();
    }
    else {
      myValueText.setText("");
    }
  }

  protected JComponent createCenterPanel() {
    fillPropertyNames(myFiles);
    if (myPropertyName != null) {
      myPropertyNameBox.getEditor().setItem(myPropertyName);
      myPropertyNameBox.getEditor().selectAll();
    }
    mySetPropertyButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (mySetPropertyButton.isSelected()) {
          myValueText.setEnabled(true);
          myValueText.requestFocus();
        }
        else {
          myValueText.setEnabled(false);
        }
      }
    });
    myRecursiveButton.setEnabled(myIsRecursionAllowed);
    return myMainPanel;
  }

  private void fillPropertyNames(File[] files) {
    final Collection<String> names = new TreeSet<String>();
    if (files.length == 1) {
      File file = files[0];
      try {
        SVNWCClient client = myVCS.createWCClient();
        client.doGetProperty(file, null, SVNRevision.WORKING, SVNRevision.WORKING, false,
                             new ISVNPropertyHandler() {
                               public void handleProperty(File path, SVNPropertyData property) {
                                 String name = property.getName();
                                 if (name != null) {
                                   names.add(name);
                                 }
                               }
                               public void handleProperty(SVNURL url, SVNPropertyData property) {
                               }
                               public void handleProperty(long revision, SVNPropertyData property) {
                               }
                             });
      }
      catch (SVNException e) {
        //
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
