package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.properties.charset.AsciiToNativeCharset;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:02:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileType extends LanguageFileType {
  public PropertiesFileType() {
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
    return IconLoader.getIcon("/fileTypes/uiForm.png");
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

      public boolean hasRename() {
        return true;
      }
    };
  }

  public String getCharset(VirtualFile file) {
    if (System.getProperty("NATIVE2ASCII") == null) {
      return null;
    }
    else {
      return AsciiToNativeCharset.INSTANCE.name();
    }
  }
}
