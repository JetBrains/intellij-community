/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.application.options;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeStyleXmlPanel extends CodeStyleAbstractPanel{
  private JTextField myKeepBlankLines;
  private JComboBox myWrapAttributes;
  private JCheckBox myAlignAttributes;
  private JCheckBox myKeepWhiteSpaces;

  private JPanel myPanel;
  private JPanel myPreviewPanel;

  private JCheckBox mySpacesAroundEquality;
  private JCheckBox mySpacesAroundTagName;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    fillWrappingCombo(myWrapAttributes);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updatePreview();
      }
    };

    myKeepBlankLines.addActionListener(actionListener);
    myWrapAttributes.addActionListener(actionListener);
    myKeepWhiteSpaces.addActionListener(actionListener);
    myAlignAttributes.addActionListener(actionListener);
    mySpacesAroundEquality.addActionListener(actionListener);
    mySpacesAroundTagName.addActionListener(actionListener);

    myKeepBlankLines.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void insertUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void removeUpdate(DocumentEvent e) {
        updatePreview();
      }
    });
  }

  public void apply(CodeStyleSettings settings) {
    settings.XML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.XML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.XML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.XML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.XML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

  }

  private int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl() {
    myKeepBlankLines.setText(String.valueOf(mySettings.XML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(mySettings.HTML_TEXT_WRAP));
    myAlignAttributes.setSelected(mySettings.XML_ALIGN_ATTRIBUTES);
    myKeepWhiteSpaces.setSelected(mySettings.XML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(mySettings.XML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.XML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.XML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }
    if (settings.XML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }
    if (settings.XML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.XML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()){
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return "<idea-plugin>\n" +
    "  <name>CVS</name>\n" +
    "  <description>CVS integration</description>\n" +
    "  <version>0.1</version>\n" +
    "  <vendor>IntelliJ</vendor>\n" +
    "  <idea-version min=\"4.0\" max=\"4.0\"/>\n" +
    "\n" +
    "  <application-components>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration</implementation-class>\n" +
    "    </component>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.application.CvsEntriesManager</implementation-class>\n" +
    "    </component>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.connections.ssh.SSHPasswordProvider</implementation-class>\n" +
    "      <option name=\"workspace\" value=\"true\"/>\n" +
    "    </component>\n" +
    "\n" +
    "    <component>\n" +
    "      <interface-class>com.intellij.openapi.cvsIntegration.CvsServices</interface-class>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.impl.CvsServicesImpl</implementation-class>\n" +
    "    </component>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.config.ImportConfiguration</implementation-class>\n" +
    "    </component>\n" +
    "\n" +
    "  </application-components>\n" +
    "\n" +
    "  <project-components>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.config.CvsConfiguration</implementation-class>\n" +
    "      <option name=\"workspace\" value=\"true\"/>\n" +
    "    </component>\n" +
    "\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.CvsVcs2</implementation-class>\n" +
    "    </component>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.ui.CvsTabbedWindow</implementation-class>\n" +
    "      <option name=\"workspace\" value=\"true\"/>\n" +
    "    </component>\n" +
    "    <component>\n" +
    "      <implementation-class>com.intellij.cvsSupport2.application.CvsStorageSupportingDeletionComponent</implementation-class>\n" +
    "    </component>\n" +
    "\n" +
    "  </project-components>\n" +
    "\n" +
    "\n" +
    "  <actions>\n" +
    "\n" +
    "    <group id=\"CvsFileGroup\" text=\"CVS\">\n" +
    "      <separator/>\n" +
    "      <add-to-group group-id=\"FileMenu\" anchor=\"before\" relative-to-action=\"ExportToHTML\"/>\n" +
    "      <action id=\"Cvs.CheckoutProject\" class=\"com.intellij.cvsSupport2.actions.CheckoutAction\" text=\"C_heck Out from CVS...\" description=\"Get a copy of files from a CVS repository\"/>\n" +
    "      <action id=\"Cvs.Import\" class=\"com.intellij.cvsSupport2.actions.ImportAction\" text=\"Im_port into CVS...\" description=\"Put files into a CVS repository\"/>\n" +
    "      <action id=\"Cvs.BrowseCVSRepository\" class=\"com.intellij.cvsSupport2.actions.BrowseCvsRepositoryAction\" text=\"_Browse CVS Repository...\"\n" +
    "        description=\"Browse a CVS repository\"/>\n" +
    "      <separator/>\n" +
    "    </group>\n" +
    "\n" +
    "    <group id=\"CvsGlobalGroup\" class=\"com.intellij.cvsSupport2.actions.Cvs2Group\" test=\"CVS\" text=\"_CVS\" popup=\"true\">\n" +
    "      <add-to-group group-id=\"VcsGroups\" anchor=\"last\"/>\n" +
    "      <separator/>\n" +
    "      <action id=\"GlobalSettings\" class=\"com.intellij.cvsSupport2.actions.GlobalSettingsAction\" text=\"_Global Settings\" description=\"Configure global CVS settings\"/>\n" +
    "      <action id=\"ConfigureCvsRoots\" class=\"com.intellij.cvsSupport2.actions.ConfigureCvsRootsAction\" text=\"Configure CVS _Roots\" description=\"Configure CVSroots\"/>\n" +
    "      <separator/>\n" +
    "    </group>\n" +
    "\n" +
    "    <group id=\"CvsFilePopupGroup\" class=\"com.intellij.cvsSupport2.actions.Cvs2Group\" test=\"CVS\" text=\"_CVS\" popup=\"true\">\n" +
    "      <add-to-group group-id=\"VcsGroup\" anchor=\"last\"/>\n" +
    "    </group>\n" +
    "\n" +
    "\n" +
    "    <group id=\"UpdateActionGroup\">\n" +
    "      <reference id=\"$Delete\"/>\n" +
    "      <action id=\"Cvs.GetFromRepository\" class=\"com.intellij.cvsSupport2.actions.GetFileFromRepositoryAction\" text=\"Get\" description=\"Get from cvs repository\"/>\n" +
    "      <separator/>\n" +
    "    </group>\n" +
    "    <group id=\"AddOptionDialogActionGroup\" test=\"\"\n" +
    "      text=\"AddOptionDialogActionGroup\">\n" +
    "      <action id=\"Cvs.Ignore\" class=\"com.intellij.cvsSupport2.actions.IgnoreFileAction\" text=\"I_gnore\" description=\"Add file/directory to CVS ignore list ('.cvsignore')\">\n" +
    "        <add-to-group group-id=\"Vcs.CheckinProjectPopup\" anchor=\"last\"/>\n" +
    "      </action>\n" +
    "    </group>\n" +
    "\n" +
    "    <group id=\"CvsActions\">\n" +
    "      <reference id=\"CheckinFiles\"/>\n" +
    "      <action id=\"Cvs.Rollback\" class=\"com.intellij.cvsSupport2.actions.CvsRollbackChangesAction\" text=\"Ro_llback Changes\" icon=\"/actions/rollback.png\" description=\"Rollback local changes\"/>\n" +
    "      <reference id=\"UpdateFiles\"/>\n" +
    "      <action id=\"Cvs.Checkout\" class=\"com.intellij.cvsSupport2.actions.CheckoutFileAction\" text=\"Chec_k Out\" description=\"Check out file or directory (and overwrite local version)\"/>\n" +
    "      <action id=\"Cvs.MergeAction\" class=\"com.intellij.cvsSupport2.actions.MergeAction\" text=\"_Merge\" description=\"Merge file visually\"/>\n" +
    "\n" +
    "      <separator/>\n" +
    "\n" +
    "\n" +
    "      <action id=\"Cvs.Add\" class=\"com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction\" text=\"_Add\" description=\"Add file/directory to CVS\"/>\n" +
    "      <action id=\"Cvs.UndoAdd\" class=\"com.intellij.cvsSupport2.actions.UnmarkAddedAction\" text=\"Undo 'Add'\" description=\"Undo adding file to CVS\"/>\n" +
    "      <action id=\"Cvs.Remove\" class=\"com.intellij.cvsSupport2.actions.RemoveLocallyDeletedFilesAction\" text=\"Remove\" description=\"Remove file from CVS\"/>\n" +
    "      <reference id=\"Cvs.Ignore\"/>\n" +
    "\n" +
    "      <separator/>\n" +
    "      <action id=\"Cvs.Diff\" class=\"com.intellij.cvsSupport2.actions.DiffAction\" text=\"Compare with The _Same Version\" description=\"Show local changes to the file\"/>\n" +
    "      <action id=\"Cvs.CompareWithLastVersion\" class=\"com.intellij.cvsSupport2.actions.DiffWithCvsVersionAction\" text=\"Compare with _Latest Repository Version\"\n" +
    "        description=\"Compare local copy with the latest version from repository\"/>\n" +
    "      <action id=\"Cvs.DiffWithSelectedVersion\" class=\"com.intellij.cvsSupport2.actions.DiffWithSelectedVersionAction\"\n" +
    "        text=\"Compare with Se_lected Version...\" description=\"Compare local copy with some selected version\"/>\n" +
    "\n" +
    "      <reference id=\"Vcs.ShowTabbedFileHistory\"/>\n" +
    "      <reference id=\"Vcs.ShowHistoryForBlock\"/>\n" +
    "      <action id=\"Cvs.Annotate\" class=\"com.intellij.cvsSupport2.actions.AnnotateToggleAction\" text=\"A_nnotate\" description=\"Show information about last modification date and author for each line\"/>\n" +
    "\n" +
    "      <separator/>\n" +
    "      <action id=\"Cvs.CreateBranch\" class=\"com.intellij.cvsSupport2.actions.BranchAction\" text=\"Create _Branch...\" description=\"Create branch in CVS repository for file/directory\"/>\n" +
    "      <action id=\"Cvs.CreateTag\" class=\"com.intellij.cvsSupport2.actions.CreateTagAction\" text=\"C_reate Tag...\" description=\"Create tag in CVS repository for file/directory\"/>\n" +
    "      <action id=\"Cvs.DeleteTag\" class=\"com.intellij.cvsSupport2.actions.DeleteTagAction\" text=\"_Delete Tag...\" description=\"Delete tag from CVS repository for file/directory\"/>\n" +
    "      <separator/>\n" +
    "      <group id=\"EditAndWatch\" text=\"Ed_it and Watch\" popup=\"true\">\n" +
    "        <action id=\"Cvs.Edit\" class=\"com.intellij.cvsSupport2.actions.EditAction\" text=\"_Edit\" description=\"Start editing of file\"/>\n" +
    "        <action id=\"Cvs.Unedit\" class=\"com.intellij.cvsSupport2.actions.UneditAction\" text=\"_Unedit\" description=\"Cancel editing of file and undo local changes\"/>\n" +
    "        <action id=\"Cvs.Editors\" class=\"com.intellij.cvsSupport2.actions.ViewEditorsAction\" text=\"_Show Editors\" description=\"Show who is editing file/directory\"/>\n" +
    "        <separator/>\n" +
    "        <action id=\"Cvs.WatchOn\" class=\"com.intellij.cvsSupport2.actions.WatchOnAction\" text=\"Watch O_n\" description=\"Start watching file/directory\"/>\n" +
    "        <action id=\"Cvs.WatchOff\" class=\"com.intellij.cvsSupport2.actions.WatchOffAction\" text=\"Watch O_ff\" description=\"Stop watching file/directory\"/>\n" +
    "        <action id=\"Cvs.WatchAdd\" class=\"com.intellij.cvsSupport2.actions.WatchAddAction\" text=\"_Add Watch...\" description=\"Add watch for file/directory\"/>\n" +
    "        <action id=\"Cvs.WatchRemove\" class=\"com.intellij.cvsSupport2.actions.WatchRemoveAction\" text=\"_Remove Watch...\" description=\"Remove watch for file/directory\"/>\n" +
    "        <action id=\"Cvs.Watchers\" class=\"com.intellij.cvsSupport2.actions.ViewWatchersAction\" text=\"Show Watc_hers\" description=\"Show who is watching file/directory\"/>\n" +
    "      </group>\n" +
    "      <add-to-group group-id=\"UpdateActionGroup\" anchor=\"last\"/>\n" +
    "\n" +
    "      <action id=\"Cvs.CheckinProjectPanel.Add\" class=\"com.intellij.cvsSupport2.checkinProject.AddUnknownFileToCvsAction\"\n" +
    "        text=\"Add to CVS...\" description=\"Add to CVS (used in Commit Project dialog only)\">\n" +
    "        <add-to-group group-id=\"Vcs.CheckinProjectPopup\" anchor=\"last\"/>\n" +
    "      </action>\n" +
    "\n" +
    "      <action id=\"Cvs.CheckinProjectPanel.Delete\" class=\"com.intellij.cvsSupport2.actions.RemoveLocallyDeletedFilesAction\"\n" +
    "        text=\"Remove from CVS...\" description=\"Remove from CVS (used in Commit Project dialog only)\">\n" +
    "        <add-to-group group-id=\"Vcs.CheckinProjectPopup\" anchor=\"last\"/>\n" +
    "      </action>\n" +
    "\n" +
    "      <add-to-group group-id=\"CvsFilePopupGroup\" anchor=\"last\"/>\n" +
    "      <add-to-group group-id=\"CvsGlobalGroup\" anchor=\"last\"/>\n" +
    "    </group>\n" +
    "  </actions>\n" +
    "</idea-plugin>";
  }

  protected FileType getFileType() {
    return StdFileTypes.XML;
  }
}
