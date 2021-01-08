// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.application.options.*;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @NotNull
  @Override
  public CodeStyleConfigurable createConfigurable(@NotNull final CodeStyleSettings settings, @NotNull final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, YAMLLanguage.INSTANCE.getDisplayName()) {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        final CodeStyleSettings currentSettings = getCurrentSettings();
        return new TabbedLanguageCodeStylePanel(YAMLLanguage.INSTANCE, currentSettings, settings) {
          @Override
          protected void initTabs(final CodeStyleSettings settings) {
            addIndentOptionsTab(settings);
            addSpacesTab(settings);
            addWrappingAndBracesTab(settings);
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

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
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

  @NotNull
  @Override
  public Language getLanguage() {
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
        new String[] {"do_not_align", "on_colon", "on_value"}
      );
    }
    return super.getAccessor(codeStyleObject, field);
  }
}
