// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.table.TableView;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.ui.TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.util.PathUtil.toSystemDependentName;
import static java.util.Arrays.asList;

public class DownloadDictionaryDialog extends DialogWrapper {
  private JComboBox<String> myDictionaryCombobox;
  private TextFieldWithBrowseButton myDirectoryTextField;
  private JPanel myMainPanel;
  private final Project myProject;
  private final TableView<String> myTableView;
  private static final String DIC = ".dic";
  private static final String AFF = ".aff";
  private static final String PATH = "https://raw.githubusercontent.com/bzixilu/dictionaries/master/";


  protected DownloadDictionaryDialog(@NotNull Project project, @NotNull TableView<String> tableView) {
    super(project, true);
    myProject = project;
    myTableView = tableView;
    setTitle(SpellCheckerBundle.message("choose.dictionary.to.add"));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myDictionaryCombobox.setModel(new DefaultComboBoxModel<>(namesToPaths.keySet().toArray(new String[namesToPaths.size()])));
    myDirectoryTextField.setText(myProject.getBasePath());
    FileChooserDescriptor singleFileDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myDirectoryTextField.addActionListener(new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(
      SpellCheckerBundle.message("choose.directory.to.save.dictionary.title"),
      SpellCheckerBundle.message("choose.directory.to.save.dictionary"), myDirectoryTextField, myProject, singleFileDescriptor,
      TEXT_FIELD_WHOLE_TEXT) {

      @Nullable
      @Override
      protected VirtualFile getInitialFile() {
        String text = myDirectoryTextField.getText();
        if (StringUtil.isEmpty(text)) {
          VirtualFile file =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemDependentName(myDirectoryTextField.getText()));
          if (file != null) {
            return file;
          }
        }
        return super.getInitialFile();
      }
    });

    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    final String dictionaryName = (String)myDictionaryCombobox.getSelectedItem();
    final VirtualFile directory = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemDependentName(myDirectoryTextField.getText()));

    if (dictionaryName != null && directory != null) {
      downloadDictionary(dictionaryName, directory);
    }
    super.doOKAction();
  }

  private void downloadDictionary(@NotNull String name, @NotNull VirtualFile dir) {

    final DownloadableFileService downloader = DownloadableFileService.getInstance();
    final List<VirtualFile> files = downloader.createDownloader(asList(downloader.createFileDescription(getUrl(name, DIC), name + DIC),
                                                                       downloader.createFileDescription(getUrl(name, AFF), name + AFF)),
                                                                name)
                                              .downloadFilesWithProgress((dir).getPath(), myProject, null);
    if (files != null && files.size() == 2) {
      files.stream().map(file -> toSystemDependentName(file.getPath()))
           .filter(path -> extensionEquals(path, "dic") && !myTableView.getItems().contains(path))
           .forEach(path -> myTableView.getListTableModel().addRow(path));
    }
  }


  @NotNull
  private static String getUrl(@NotNull String name, @NotNull String extension) {
    return PATH + namesToPaths.get(name) + extension;
  }

  private static final Map<String, String> namesToPaths = ImmutableMap.<String, String>builder()
    .put("English", "en/en_GB")
    .put("Russian", "ru_RU/ru_RU")
    .put("French", "fr_FR/fr_FR")
    .put("German", "de/de_DE_frami")
    .build();
}
