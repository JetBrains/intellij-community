package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.NlsContexts;
import de.plushnikov.intellij.plugin.LombokBundle;
import icons.LombokIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public final class LombokConfigColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor(LombokBundle.messagePointer("color.settings.comment"), LombokConfigSyntaxHighlighter.COMMENT),
    new AttributesDescriptor(LombokBundle.messagePointer("color.settings.clear"), LombokConfigSyntaxHighlighter.CLEAR),
    new AttributesDescriptor(LombokBundle.messagePointer("color.settings.key"), LombokConfigSyntaxHighlighter.KEY),
    new AttributesDescriptor(LombokBundle.messagePointer("color.settings.separator"), LombokConfigSyntaxHighlighter.SEPARATOR),
    new AttributesDescriptor(LombokBundle.messagePointer("color.settings.value"), LombokConfigSyntaxHighlighter.VALUE),
  };

  @Override
  public @Nullable Icon getIcon() {
    return LombokIcons.Config;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new LombokConfigSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      ##
      ## Key : lombok.log.fieldName
      ## Type: string
      ##
      ## Use this name for the generated logger fields (default: 'log')
      ##
      ## Examples:
      #
      clear lombok.log.fieldName
      lombok.log.fieldName = LOGGER
      """;
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull @NlsContexts.ConfigurableName String getDisplayName() {
    return LombokBundle.message("configurable.name.lombok.config");
  }
}
