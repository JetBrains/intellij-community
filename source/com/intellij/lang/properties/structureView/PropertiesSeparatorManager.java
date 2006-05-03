/**
 * @author cdr
 */
package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import gnu.trove.THashMap;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.io.UnsupportedEncodingException;

public class PropertiesSeparatorManager implements JDOMExternalizable, ApplicationComponent {
  @NonNls private static final String FILE_ELEMENT = "file";
  @NonNls private static final String URL_ELEMENT = "url";
  @NonNls private static final String SEPARATOR_ATTR = "separator";

  public static PropertiesSeparatorManager getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesSeparatorManager.class);
  }

  private final Map<VirtualFile, String> mySeparators = new THashMap<VirtualFile, String>();

  public String getSeparator(Project project, VirtualFile file) {
    String separator = mySeparators.get(file);
    if (separator == null) {
      separator = guessSeparator(project, file);
      setSeparator(file, separator);
    }
    return separator;
  }

  //returns most probable separator in properties files
  private static String guessSeparator(final Project project, final VirtualFile file) {
    Collection<PropertiesFile> files;
    if (file instanceof ResourceBundleAsVirtualFile) {
      files = ((ResourceBundleAsVirtualFile)file).getResourceBundle().getPropertiesFiles(project);
    }
    else {
      PsiManager psiManager = PsiManager.getInstance(project);
      files = Collections.singletonList((PropertiesFile)psiManager.findFile(file));
    }
    final TIntLongHashMap charCounts = new TIntLongHashMap();
    for (PropertiesFile propertiesFile : files) {
      if (propertiesFile == null) continue;
      List<Property> properties = propertiesFile.getProperties();
      for (Property property : properties) {
        String key = property.getKey();
        if (key == null) continue;
        for (int i =0; i<key.length(); i++) {
          char c = key.charAt(i);
          if (!Character.isLetterOrDigit(c)) {
            charCounts.put(c, charCounts.get(c) + 1);
          }
        }
      }
    }

    final char[] mostProbableChar = new char[]{'.'};
    charCounts.forEachKey(new TIntProcedure() {
      long count = -1;
      public boolean execute(int ch) {
        long charCount = charCounts.get(ch);
        if (charCount > count) {
          count = charCount;
          mostProbableChar[0] = (char)ch;
        }
        return true;
      }
    });
    if (mostProbableChar[0] == 0) {
      mostProbableChar[0] = '.';
    }
    return Character.toString(mostProbableChar[0]);
  }

  public void setSeparator(VirtualFile file, String separator) {
    mySeparators.put(file, separator);
  }

  @NotNull
  public String getComponentName() {
    return "PropertiesSeparatorManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void readExternal(Element element) throws InvalidDataException {
    List<Element> files = element.getChildren(FILE_ELEMENT);
    for (Element fileElement : files) {
      String url = fileElement.getAttributeValue(URL_ELEMENT, "");
      String separator = fileElement.getAttributeValue(SEPARATOR_ATTR,"");
      try {
        @NonNls String baseCharset = "ISO-8859-1";
        separator = new String(separator.getBytes(baseCharset), Native2AsciiCharset.makeNative2AsciiEncodingName(baseCharset));
      }
      catch (UnsupportedEncodingException e) {
        //can't be
      }
      VirtualFile file;
      ResourceBundle resourceBundle = ResourceBundleImpl.createByUrl(url);
      if (resourceBundle != null) {
        file = new ResourceBundleAsVirtualFile(resourceBundle);
      }
      else {
        file = VirtualFileManager.getInstance().findFileByUrl(url);
      }
      if (file != null) {
        mySeparators.put(file, separator);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (VirtualFile file : mySeparators.keySet()) {
      String url;
      if (file instanceof ResourceBundleAsVirtualFile) {
        ResourceBundle resourceBundle = ((ResourceBundleAsVirtualFile)file).getResourceBundle();
        url = ((ResourceBundleImpl)resourceBundle).getUrl();
      }
      else {
        url = file.getUrl();
      }
      String separator = mySeparators.get(file);
      StringBuffer encoded = new StringBuffer(separator.length());
      for (int i=0;i<separator.length();i++) {
        char c = separator.charAt(i);
        encoded.append("\\u");
        encoded.append(Character.forDigit(c >> 12, 16));
        encoded.append(Character.forDigit((c >> 8) & 0xf, 16));
        encoded.append(Character.forDigit((c >> 4) & 0xf, 16));
        encoded.append(Character.forDigit(c & 0xf, 16));
      }
      Element fileElement = new Element(FILE_ELEMENT);
      fileElement.setAttribute(URL_ELEMENT, url);
      fileElement.setAttribute(SEPARATOR_ATTR, encoded.toString());
      element.addContent(fileElement);
    }
  }

}
