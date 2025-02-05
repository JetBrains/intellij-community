// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.settingsSync;

import com.intellij.application.options.*;
import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings;

import javax.swing.*;
import java.lang.reflect.Field;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

public class YAMLLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  private static class Holder {
    private static final int[] ALIGN_VALUES = new int[]{
      YAMLCodeStyleSettings.DO_NOT_ALIGN,
      YAMLCodeStyleSettings.ALIGN_ON_COLON,
      YAMLCodeStyleSettings.ALIGN_ON_VALUE
    };

    private static final String[] ALIGN_OPTIONS = new String[]{
      YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.align.options.no"),
      YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.align.options.colon"),
      YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.align.options.value")
    };
  }

  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(final @NotNull CodeStyleSettings settings,
                                                           final @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, YAMLLanguage.INSTANCE.getDisplayName()) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        final CodeStyleSettings currentSettings = getCurrentSettings();
        return new TabbedLanguageCodeStylePanel(YAMLLanguage.INSTANCE, currentSettings, settings) {
          @Override
          protected void initTabs(final CodeStyleSettings settings) {
            addIndentOptionsTab(settings);
            addSpacesTab(settings);
            addWrappingAndBracesTab(settings);
            addTab(new GenerationCodeStylePanel(settings, YAMLLanguage.INSTANCE));
          }
        };
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.yaml";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return YAMLLanguage.INSTANCE.getDisplayName();
  }

  @Override
  public @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new YAMLCodeStyleSettings(settings);
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;
    indentOptions.USE_TAB_CHARACTER = false;
    commonSettings.SPACE_WITHIN_BRACES = true;
    commonSettings.SPACE_WITHIN_BRACKETS = true;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new YAMLIndentOptionsEditor(this);
  }

  @Override
  public @NotNull Language getLanguage() {
    return YAMLLanguage.INSTANCE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.INDENT_SETTINGS) {
      consumer.showStandardOptions("INDENT_SIZE", "KEEP_INDENTS_ON_EMPTY_LINES");
    }
    else if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("SPACE_WITHIN_BRACES", "SPACE_WITHIN_BRACKETS");
      consumer.showCustomOption(YAMLCodeStyleSettings.class, "SPACE_BEFORE_COLON", YAMLBundle.message(
        "YAMLLanguageCodeStyleSettingsProvider.label.before"), getInstance().SPACES_OTHER);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("KEEP_LINE_BREAKS");

      consumer.showCustomOption(YAMLCodeStyleSettings.class,
                                "ALIGN_VALUES_PROPERTIES",
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.align.values"),
                                null,
                                Holder.ALIGN_OPTIONS,
                                Holder.ALIGN_VALUES);

      consumer.showCustomOption(YAMLCodeStyleSettings.class,
                                "SEQUENCE_ON_NEW_LINE",
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.sequence.on.new.line"),
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.group.sequence.value"));

      consumer.showCustomOption(YAMLCodeStyleSettings.class,
                                "BLOCK_MAPPING_ON_NEW_LINE",
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.block.mapping.on.new.line"),
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.group.sequence.value"));

      consumer.showCustomOption(YAMLCodeStyleSettings.class,
                                "AUTOINSERT_SEQUENCE_MARKER",
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.autoinsert.sequence.marker"),
                                YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.group.sequence.value"));
    }
    else if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      consumer.showStandardOptions(
        CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name(),
        CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE.name(),
        CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE_ON_REFORMAT.name()
      );
    }
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return CodeStyleAbstractPanel.readFromFile(YAMLBundle.class, "indents.yml");
  }

  private static class YAMLIndentOptionsEditor extends SmartIndentOptionsEditor {
    private JCheckBox myIndentSequence;

    YAMLIndentOptionsEditor(@Nullable LanguageCodeStyleSettingsProvider provider) {
      super(provider);
    }

    @Override
    protected void addComponents() {
      super.addComponents();

      myIndentSequence = new JCheckBox(YAMLBundle.message("YAMLLanguageCodeStyleSettingsProvider.indent.sequence.value"));
      add(myIndentSequence);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      myIndentSequence.setEnabled(enabled);
    }

    @Override
    public boolean isModified(@NotNull CodeStyleSettings settings, @NotNull CommonCodeStyleSettings.IndentOptions options) {
      boolean isModified = super.isModified(settings, options);
      YAMLCodeStyleSettings yamlSettings = settings.getCustomSettings(YAMLCodeStyleSettings.class);

      isModified |= isFieldModified(myIndentSequence, yamlSettings.INDENT_SEQUENCE_VALUE);

      return isModified;
    }

    @Override
    public void apply(@NotNull CodeStyleSettings settings, @NotNull CommonCodeStyleSettings.IndentOptions options) {
      super.apply(settings, options);

      YAMLCodeStyleSettings yamlSettings = settings.getCustomSettings(YAMLCodeStyleSettings.class);
      yamlSettings.INDENT_SEQUENCE_VALUE = myIndentSequence.isSelected();
    }

    @Override
    public void reset(@NotNull CodeStyleSettings settings, @NotNull CommonCodeStyleSettings.IndentOptions options) {
      super.reset(settings, options);

      YAMLCodeStyleSettings yamlSettings = settings.getCustomSettings(YAMLCodeStyleSettings.class);
      myIndentSequence.setSelected(yamlSettings.INDENT_SEQUENCE_VALUE);
    }
  }

  @Override
  public @Nullable CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject,
                                                      @NotNull Field field) {
    if (codeStyleObject instanceof YAMLCodeStyleSettings && "ALIGN_VALUES_PROPERTIES".equals(field.getName())) {
      return new MagicIntegerConstAccessor(
        codeStyleObject, field,
        Holder.ALIGN_VALUES,
        new String[]{"do_not_align", "on_colon", "on_value"}
      );
    }
    return super.getAccessor(codeStyleObject, field);
  }
}

class GenerationCodeStylePanel extends CodeStyleAbstractPanel {

  private final CommenterForm myCommenterForm;

  GenerationCodeStylePanel(@NotNull CodeStyleSettings settings, Language language) {
    super(settings);
    myCommenterForm = new CommenterForm(language);
    myCommenterForm.getCommenterPanel().setBorder(
      IdeBorderFactory.createTitledBorder(YAMLBundle.message("settings.comment.label"), true, new JBInsets(10, 10, 10, 10)));
  }

  @Override
  protected @NlsContexts.TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("title.code.generation");
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    return null;
  }

  @Override
  protected @NotNull FileType getFileType() {
    return UnknownFileType.INSTANCE;
  }

  @Override
  protected @Nullable String getPreviewText() {
    return null;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myCommenterForm.apply(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myCommenterForm.isModified(settings);
  }

  @Override
  public @Nullable JComponent getPanel() {
    return myCommenterForm.getCommenterPanel();
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myCommenterForm.reset(settings);
  }
}