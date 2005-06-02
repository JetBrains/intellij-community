package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.lang.properties.charset.Native2AsciiCharset;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:02:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileType extends LanguageFileType {
  public static final Icon FILE_ICON = IconLoader.getIcon("/fileTypes/properties.png");
  public static final LanguageFileType FILE_TYPE = new PropertiesFileType();

  private PropertiesFileType() {
    super(new PropertiesLanguage());
  }

  public String getName() {
    return "Properties";
  }

  public String getDescription() {
    return "Properties";
  }

  public String getDefaultExtension() {
    return "properties";
  }

  public Icon getIcon() {
    return FILE_ICON;
  }

  public FileTypeSupportCapabilities getSupportCapabilities() {
    return new FileTypeSupportCapabilities() {
      public boolean hasCompletion() {
        return true;
      }

      public boolean hasValidation() {
        return true;
      }

      public boolean hasFindUsages() {
        return true;
      }

      public boolean hasNavigation() {
        return true;
      }

    };
  }

  public String getCharset(VirtualFile file) {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (editorSettings.isNative2AsciiForPropertiesFiles()) {
      return Native2AsciiCharset.INSTANCE.name();
    }
    else {
      String defaultCharsetName = editorSettings.getDefaultPropertiesCharsetName();
      if ("System Default".equals(defaultCharsetName)) defaultCharsetName = null;
      return defaultCharsetName;
      //return "ISO-8859-1";
    }
  }
}
