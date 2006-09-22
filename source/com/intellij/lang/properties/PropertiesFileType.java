package com.intellij.lang.properties;

import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author max
 */
public class PropertiesFileType extends LanguageFileType {
  public static final Icon FILE_ICON = IconLoader.getIcon("/fileTypes/properties.png");
  public static final LanguageFileType FILE_TYPE = new PropertiesFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "properties";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = ".properties";

  private PropertiesFileType() {
    super(new PropertiesLanguage());
  }

  @NotNull
  public String getName() {
    return "Properties";
  }

  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("properties.files.file.type.description");
  }

  @NotNull
  public String getDefaultExtension() {
    return "properties";
  }

  public Icon getIcon() {
    return FILE_ICON;
  }

  public String getCharset(@NotNull VirtualFile file) {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (editorSettings == null) return null;
    String defaultCharsetName = editorSettings.getDefaultPropertiesCharsetName();
    if (CharsetSettings.SYSTEM_DEFAULT_CHARSET_NAME.equals(defaultCharsetName)) defaultCharsetName = null;
    if (editorSettings.isNative2AsciiForPropertiesFiles()) {
      return Native2AsciiCharset.makeNative2AsciiEncodingName(defaultCharsetName);
    }
    else {
      return defaultCharsetName;
      //return "ISO-8859-1";
    }
  }
}
