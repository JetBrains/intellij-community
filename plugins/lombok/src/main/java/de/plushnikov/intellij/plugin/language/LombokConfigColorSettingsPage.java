package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.NlsContexts;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.icon.LombokIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class LombokConfigColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor(LombokBundle.message("attribute.descriptor.key"), LombokConfigSyntaxHighlighter.KEY),
    new AttributesDescriptor(LombokBundle.message("attribute.descriptor.separator"), LombokConfigSyntaxHighlighter.SEPARATOR),
    new AttributesDescriptor(LombokBundle.message("attribute.descriptor.value"), LombokConfigSyntaxHighlighter.VALUE),
  };

  @Nullable
  @Override
  public Icon getIcon() {
    return LombokIcons.CONFIG_FILE_ICON;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return new LombokConfigSyntaxHighlighter();
  }

  @NotNull
  @Override
  public String getDemoText() {
    return "##\n" +
      "## Key : lombok.log.fieldName\n" +
      "## Type: string\n" +
      "##\n" +
      "## Use this name for the generated logger fields (default: 'log')\n" +
      "##\n" +
      "## Examples:\n" +
      "#\n" +
      "clear lombok.log.fieldName\n" +
      "lombok.log.fieldName = LOGGER\n";
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  @NlsContexts.ConfigurableName
  public String getDisplayName() {
    return LombokBundle.message("configurable.name.lombok.config");
  }
}
