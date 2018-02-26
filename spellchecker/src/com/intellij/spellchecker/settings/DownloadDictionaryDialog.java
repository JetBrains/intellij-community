// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.openapi.ui.TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.project.ProjectKt.getProjectStoreDirectory;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.PathUtil.toSystemDependentName;
import static java.util.Arrays.asList;

public class DownloadDictionaryDialog extends DialogWrapper {
  private static final String ENGLISH_USA = "English (USA)";
  private JComboBox<String> myDictionaryCombobox;
  private TextFieldWithBrowseButton myDirectoryTextField;
  private JPanel myMainPanel;
  private final Project myProject;
  @NotNull private final Consumer<String> myConsumer;
  private static final String DIC = ".dic";
  private static final String AFF = ".aff";
  private static final String PATH = "https://raw.githubusercontent.com/JetBrains/dictionaries/master/";


  protected DownloadDictionaryDialog(@NotNull Project project, @NotNull Consumer<String> consumer) {
    super(project, true);
    myProject = project;
    myConsumer = consumer;
    setTitle(SpellCheckerBundle.message("choose.dictionary.to.add"));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final SortedComboBoxModel<String> model = new SortedComboBoxModel<>(String::compareTo);
    model.setAll(namesToPaths.keySet());
    model.setSelectedItem(ENGLISH_USA);
    myDictionaryCombobox.setModel(model);
    myDirectoryTextField.setText(myProject.getBasePath() != null ?
                                 chooseNotNull(getProjectStoreDirectory(myProject.getBaseDir()), myProject.getBaseDir()).getPath():
                                 PathManager.getConfigPath());
    FileChooserDescriptor singleFileDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myDirectoryTextField.addActionListener(new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(
      SpellCheckerBundle.message("choose.directory.to.save.dictionary.title"),
      SpellCheckerBundle.message("choose.directory.to.save.dictionary"), myDirectoryTextField, myProject, singleFileDescriptor,
      TEXT_FIELD_WHOLE_TEXT) {

      @Nullable
      @Override
      protected VirtualFile getInitialFile() {
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myDirectoryTextField.getText());
        if (file != null) {
          return file;
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
      files.stream()
           .map(file -> toSystemDependentName(file.getPath()))
           .filter(path -> extensionEquals(path, "dic"))
           .forEach(myConsumer);
    }
  }


  @NotNull
  private static String getUrl(@NotNull String name, @NotNull String extension) {
    return PATH + namesToPaths.get(name) + extension;
  }

  private static final Map<String, String> namesToPaths = ImmutableMap.<String, String>builder()
    .put("Afrikaans (South Africa)", "af_ZA/af_ZA")
    .put("Aragonese", "an_ES/an_ES")
    .put("Arabic", "ar/ar")
    .put("Belarusian", "be_BY/be_BY")
    .put("Bulgarian", "bg_BG/bg_BG")
    .put("Bengali", "bn_BD/bn_BD")
    .put("Breton", "br_FR/br_FR")
    .put("Bosnian", "bs_BA/bs_BA")
    .put("Czech", "cs_CZ/cs_CZ")
    .put("Dutch (Denmark)", "da_DK/da_DK")
    .put("German (Germany)", "de/de_DE_frami")
    .put("German (Austria)", "de/de_AT_frami")
    .put("German (Switzerland)", "de/de_CH_frami")
    .put("Greek", "el_GR/el_GR")
    .put("English (Great Britain)", "en/en_GB")
    .put("English (Canada)", "en/en_CA")
    .put(ENGLISH_USA, "en/en_US")
    .put("English (Australia)", "en/en_AU")
    .put("English (South African)", "en/en_ZA")
    .put("Spanish", "es/es_ANY")
    .put("Estonian", "et_EE/et_EE")
    .put("French", "fr_FR/fr_FR")
    .put("Gaelic (Scotland)", "gd_GB/gd_GB")
    .put("Galician", "gl/gl_ES")
    .put("Gujarati", "gu_IN/gu_IN")
    .put("Guarani (Paraguay)", "gug/gug")
    .put("Hebrew (Israel)", "he_IL/he_IL")
    .put("Hindi", "hi_IN/hi_IN")
    .put("Croatian","hr_HR/hr_HR")
    .put("Hungarian", "hu_HU/hu_HU")
    .put("Icelandic", "is/is")
    .put("Italian", "it_IT/it_IT")
    .put("Lithuanian", "lt_LT/lt_LT")
    .put("Latvian", "lv_LV/lv_LV")
    .put("Nepali", "ne_NP/ne_NP")
    .put("Dutch (Netherlands)", "nl_NL/nl_NL")
    .put("Norwegian (Bokmål)", "no/nb_NO")
    .put("Norwegian (Nynorsk)", "no/nn_NO")
    .put("Occitan", "oc_FR/oc_FR")
    .put("Polish", "pl_PL/pl_PL")
    .put("Portuguese (Brazil)", "pt_BR/pt_BR")
    .put("Portuguese (Portugal)", "pt_PT/pt_PT")
    .put("Romanian", "ro/ro_RO")
    .put("Russian", "ru_RU/ru_RU")
    .put("Sinhala", "si_LK/si_LK")
    .put("Slovak", "sk_SK/sk_SK")
    .put("Slovenian", "sl_SI/sl_SI")
    .put("Albanian", "sq_AL/sq_AL")
    .put("Serbian", "sr/sr")
    .put("Swedish (Sweden)", "sv_SE/sv_SE")
    .put("Swedish (Finland)", "sv_SE/sv_SE")
    .put("Swahili (Tanzania)", "sw_TZ/sw_TZ")
    .put("Thai", "th_TH/th_TH")
    .put("Ukrainian", "uk_UA/uk_UA")
    .put("Vietnamese", "vi/vi_VN")
    .build();
}
