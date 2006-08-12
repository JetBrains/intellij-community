package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Collection;

/**
 * @author MYakovlev
 *         Date: Jul 16, 2002
 */
public class CodeStyleSchemesImpl extends CodeStyleSchemes implements ExportableApplicationComponent,JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl");
  private HashMap<String, CodeStyleScheme> mySchemes = new HashMap<String, CodeStyleScheme>();   // name -> scheme
  private CodeStyleScheme myCurrentScheme;

  @NonNls private static final String DEFAULT_SCHEME_NAME = "Default";

  public String CURRENT_SCHEME_NAME = DEFAULT_SCHEME_NAME;
  private boolean myIsInitialized = false;
  @NonNls private static final String XML_EXTENSION = ".xml";
  @NonNls private static final String CODESTYLES_DIRECTORY = "codestyles";

  private CodeStyleSchemesImpl() {
  }

  public String getComponentName() {
    return "CodeStyleSchemes";
  }

  public void initComponent() {
    init();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    CodeStyleScheme current = findSchemeByName(CURRENT_SCHEME_NAME);
    if (current == null) current = getDefaultScheme();
    setCurrentScheme(current);
  }

  public void disposeComponent() {
  }

  public CodeStyleScheme[] getSchemes() {
    final Collection<CodeStyleScheme> schemes = mySchemes.values();
    return schemes.toArray(new CodeStyleScheme[schemes.size()]);
  }

  public CodeStyleScheme getCurrentScheme() {
    return myCurrentScheme;
  }

  public void setCurrentScheme(CodeStyleScheme scheme) {
    myCurrentScheme = scheme;
    CURRENT_SCHEME_NAME = scheme.getName();
  }

  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  public void deleteScheme(CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }
    CodeStyleSchemeImpl currScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemes.remove(scheme.getName());
  }

  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  public CodeStyleScheme findSchemeByName(String name) {
    return mySchemes.get(name);
  }

  public void addScheme(CodeStyleScheme scheme) {
    String name = scheme.getName();
    if (mySchemes.containsKey(name)) {
      LOG.error("Not unique scheme name: " + name);
    }
    mySchemes.put(name, scheme);
  }

  protected void removeScheme(CodeStyleScheme scheme) {
    mySchemes.remove(scheme.getName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    init();
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  private void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;
    mySchemes.clear();

    File[] files = getSchemeFiles();
    for (File file : files) {
      if (StringUtil.endsWithIgnoreCase(file.getName(), XML_EXTENSION)) {
        try {
          addScheme(CodeStyleSchemeImpl.readScheme(file));
        }
        catch (Exception e) {
          Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.read.scheme.file.message", file.getName()),
                                   PsiBundle.message("codestyle.cannot.read.scheme.file.title"));
        }
      }
    }

    final CodeStyleScheme[] schemes = getSchemes();
    for (CodeStyleScheme scheme : schemes) {
      ((CodeStyleSchemeImpl)scheme).init(this);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    File[] files = getSchemeFiles();
    for (File file : files) {
      String fileName = file.getName().toLowerCase();
      String xmlExtension = XML_EXTENSION;
      if (fileName.endsWith(xmlExtension)) {
        try {
          String fileNameWithoutExtension = fileName.substring(0, fileName.length() - xmlExtension.length());
          if (!mySchemes.containsKey(fileNameWithoutExtension)) {
            file.delete();
          }
        }
        catch (Exception e) {
          LOG.assertTrue(false, "Unable to save Code Style Settings");
        }
      }
    }

    final CodeStyleScheme[] schemes = getSchemes();
    for (CodeStyleScheme scheme : schemes) {
      if (!scheme.isDefault()) {
        File dir = getDir(true);
        if (dir == null) break;
        ((CodeStyleSchemeImpl)scheme).save(dir);
      }
    }

    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public File[] getExportFiles() {
    return new File[]{getDir(true), PathManager.getDefaultOptionsFile()};
  }

  public String getPresentableName() {
    return PsiBundle.message("codestyle.export.display.name");
  }

  private File[] getSchemeFiles() {
    File schemesDir = getDir(true);
    if (schemesDir == null) {
      return new File[0];
    }

    File[] files = schemesDir.listFiles();
    if (files == null) {
      LOG.error("Cannot read directory: " + schemesDir.getAbsolutePath());
      return new File[0];
    }
    return files;
  }

  private static File getDir(boolean create) {
    String directoryPath = PathManager.getConfigPath() + File.separator + CODESTYLES_DIRECTORY;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.message", directoryPath),
                                 PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.title"));
        return null;
      }
    }
    return directory;
  }
}
