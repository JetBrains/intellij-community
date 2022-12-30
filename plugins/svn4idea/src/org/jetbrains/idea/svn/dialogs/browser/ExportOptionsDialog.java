// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.LineSeparator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static java.util.Arrays.asList;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class ExportOptionsDialog extends DialogWrapper implements ActionListener {

  private final Url myURL;
  private final Project myProject;
  private final File myFile;
  private TextFieldWithBrowseButton myPathField;
  private DepthCombo myDepth;
  private JBCheckBox myExternalsCheckbox;
  private JBCheckBox myForceCheckbox;
  private final @NotNull CollectionComboBoxModel<@Nullable LineSeparator> myLineSeparatorComboBoxModel =
    new CollectionComboBoxModel<>(asList(null, LineSeparator.LF, LineSeparator.CRLF, LineSeparator.CR));

  public ExportOptionsDialog(Project project, Url url, File target) {
    super(project, true);
    myURL = url;
    myProject = project;
    myFile = target;
    setTitle(message("dialog.title.svn.export.options"));
    init();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.export.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public Depth getDepth() {
    return myDepth.getDepth();
  }

  public boolean isForce() {
    return myForceCheckbox.isSelected();
  }

  public boolean isIgnoreExternals() {
    return !myExternalsCheckbox.isSelected();
  }

  @Nullable
  public String getEOLStyle() {
    LineSeparator separator = myLineSeparatorComboBoxModel.getSelected();
    return separator != null ? separator.name() : null;
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = JBUI.insets(2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;

    panel.add(new JBLabel(message("label.export")), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JBLabel urlLabel = new JBLabel(myURL.toDecodedString());
    urlLabel.setFont(urlLabel.getFont().deriveFont(Font.BOLD));
    panel.add(urlLabel, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JBLabel(message("label.destination")), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myPathField = new TextFieldWithBrowseButton(this);
    myPathField.setText(myFile.getAbsolutePath());
    myPathField.setEditable(false);
    panel.add(myPathField, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;

    // other options.
    final JBLabel depthLabel = new JBLabel(message("label.depth.text"));
    depthLabel.setToolTipText(message("label.depth.description"));
    panel.add(depthLabel, gc);
    ++gc.gridx;
    myDepth = new DepthCombo(false);
    panel.add(myDepth, gc);
    depthLabel.setLabelFor(myDepth);

    gc.gridx = 0;
    gc.gridy += 1;
    myForceCheckbox = new JBCheckBox(message("checkbox.replace.existing.files"));
    myForceCheckbox.setSelected(true);
    panel.add(myForceCheckbox, gc);
    gc.gridy += 1;
    myExternalsCheckbox = new JBCheckBox(message("checkbox.include.externals.locations"));
    myExternalsCheckbox.setSelected(true);
    panel.add(myExternalsCheckbox, gc);
    gc.gridy += 1;
    gc.gridwidth = 2;
    panel.add(new JBLabel(message("label.override.native.eols.with")), gc);
    gc.gridx += 2;
    gc.gridwidth = 1;
    panel.add(createLineSeparatorComboBox(), gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.anchor = GridBagConstraints.SOUTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gc);
    return panel;
  }

  @NotNull
  private ComboBox<LineSeparator> createLineSeparatorComboBox() {
    ComboBox<LineSeparator> comboBox = new ComboBox<>(myLineSeparatorComboBoxModel);

    comboBox.setRenderer(SimpleListCellRenderer.create(message("combobox.crlf.none"), separator ->
      ApplicationBundle.message(switch (separator) {
        case LF -> "combobox.crlf.unix";
        case CRLF -> "combobox.crlf.windows";
        case CR -> "combobox.crlf.mac";
      })));

    return comboBox;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(message("dialog.title.export.directory"));
    fcd.setDescription(message("label.select.directory.to.export.from.subversion"));
    fcd.setHideIgnored(false);
    VirtualFile file = FileChooser.chooseFile(fcd, getContentPane(), myProject, null);
    if (file == null) {
      return;
    }
    myPathField.setText(file.getPath().replace('/', File.separatorChar));
  }
}
