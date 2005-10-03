/**
 * @author cdr
 */
package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
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
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    final TIntIntHashMap charCounts = new TIntIntHashMap();
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
      int count = -1;
      public boolean execute(int ch) {
        int charCount = charCounts.get(ch);
        if (charCount > count) {
          count = charCount;
          mostProbableChar[0] = (char)ch;
        }
        return true;
      }
    });
    return Character.toString(mostProbableChar[0]);
  }

  public void setSeparator(VirtualFile file, String separator) {
    mySeparators.put(file, separator);
  }

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
      String separator = fileElement.getAttributeValue(SEPARATOR_ATTR);
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
      Element fileElement = new Element(FILE_ELEMENT);
      fileElement.setAttribute(URL_ELEMENT, url);
      fileElement.setAttribute(SEPARATOR_ATTR, separator);
      element.addContent(fileElement);
    }
  }

}
