/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.UniqueFileNamesProvider;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

public class EditorColorsManagerImpl extends EditorColorsManager implements NamedJDOMExternalizable,
                                                                            ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl");

  private Map<String, EditorColorsScheme> mySchemesMap = new com.intellij.util.containers.HashMap<String, EditorColorsScheme>();
  private Collection<EditorColorsListener> myListeners = new ArrayList<EditorColorsListener>();

  private EditorColorsScheme myGlobalScheme;

  @NonNls private static final String NODE_NAME = "global_color_scheme";
  @NonNls private static final String SCHEME_NODE_NAME = "scheme";

  private String myGlobalSchemeName;
  public boolean USE_ONLY_MONOSPACED_FONTS = true;
  private DefaultColorSchemesManager myDefaultColorSchemesManager;
  @NonNls private static final String XML_EXT = ".xml";
  @NonNls private static final String NAME_ATTR = "name";

  public EditorColorsManagerImpl(DefaultColorSchemesManager defaultColorSchemesManager) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;
    addDefaultSchemes();
    loadAllSchemes();
    setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
  }

  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  // -------------------------------------------------------------------------
  // Schemes manipulation routines
  // -------------------------------------------------------------------------

  public void addColorsScheme(EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && scheme.getName().trim().length() > 0) {
      mySchemesMap.put(scheme.getName(), scheme);
    }
  }

  public void removeAllSchemes() {
    mySchemesMap.clear();
    addDefaultSchemes();
  }

  private void addDefaultSchemes() {
    DefaultColorsScheme[] allDefaultSchemes = myDefaultColorSchemesManager.getAllSchemes();
    for (DefaultColorsScheme defaultScheme : allDefaultSchemes) {
      mySchemesMap.put(defaultScheme.getName(), defaultScheme);
    }
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public EditorColorsScheme[] getAllSchemes() {
    ArrayList<EditorColorsScheme> schemes = new ArrayList<EditorColorsScheme>(mySchemesMap.values());
    Collections.sort(schemes, new Comparator() {
      public int compare(Object o1, Object o2) {
        EditorColorsScheme s1 = (EditorColorsScheme)o1;
        EditorColorsScheme s2 = (EditorColorsScheme)o2;

        if (isDefaultScheme(s1) && !isDefaultScheme(s2)) return -1;
        if (!isDefaultScheme(s1) && isDefaultScheme(s2)) return 1;

        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });

    return schemes.toArray(new EditorColorsScheme[schemes.size()]);
  }

  public void setGlobalScheme(EditorColorsScheme scheme) {
    myGlobalScheme = scheme == null ? DefaultColorSchemesManager.getInstance().getAllSchemes()[0] : scheme;
    fireChanges(scheme);
  }

  public EditorColorsScheme getGlobalScheme() {
    return myGlobalScheme;
  }

  public EditorColorsScheme getScheme(String schemeName) {
    return mySchemesMap.get(schemeName);
  }

  private void fireChanges(EditorColorsScheme scheme) {
    EditorColorsListener[] colorsListeners = myListeners.toArray(new EditorColorsListener[myListeners.size()]);
    for (EditorColorsListener colorsListener : colorsListeners) {
      colorsListener.globalSchemeChange(scheme);
    }
  }

  // -------------------------------------------------------------------------
  // Routines responsible for loading & saving colors schemes.
  // -------------------------------------------------------------------------

  private void loadAllSchemes() {
    File[] files = getSchemeFiles();
    for (File file : files) {
      try {
        addColorsScheme(loadScheme(file));
      }
      catch (Exception e) {
        e.printStackTrace();
        Messages.showErrorDialog(CommonBundle.message("error.reading.color.scheme.from.file.error.message", file.getName()),
                                 CommonBundle.message("corrupted.scheme.file.message.title"));
      }
    }
  }

  public void saveAllSchemes() throws IOException {
    File dir = getColorsDir(true);
    if (dir == null) return;

    File[] oldFiles = getSchemeFiles();

    int size = mySchemesMap.values().size();
    ArrayList<String> filePaths = new ArrayList<String>();
    ArrayList<Document> documents = new ArrayList<Document>();

    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();
    Iterator<EditorColorsScheme> itr = mySchemesMap.values().iterator();
    for (int i = 0; i < size; i++) {
      AbstractColorsScheme scheme = (AbstractColorsScheme)itr.next();
      if (scheme instanceof DefaultColorsScheme) continue;

      Element root = new Element(SCHEME_NODE_NAME);
      try {
        scheme.writeExternal(root);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
        return;
      }

      @NonNls String filePath = dir.getAbsolutePath() + File.separator + namesProvider.suggestName(scheme.getName()) + XML_EXT;

      documents.add(new Document(root));
      filePaths.add(filePath);
    }

    JDOMUtil.updateFileSet(oldFiles,
                           filePaths.toArray(new String[filePaths.size()]),
                           documents.toArray(new Document[documents.size()]), CodeStyleSettingsManager.getSettings(null).getLineSeparator());
  }

  private static EditorColorsScheme loadScheme(File file) throws InvalidDataException, JDOMException, IOException {
    Document document = JDOMUtil.loadDocument(file);

    if (document == null) throw new InvalidDataException();
    Element root = document.getRootElement();

    if (root == null || !SCHEME_NODE_NAME.equals(root.getName())) {
      throw new InvalidDataException();
    }

    EditorColorsSchemeImpl scheme = new EditorColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance());
    scheme.readExternal(root);

    return scheme;
  }

  private File[] getSchemeFiles() {
    File colorsDir = getColorsDir(true);
    if (colorsDir == null) {
      return new File[0];
    }

    File[] files = colorsDir.listFiles(new FileFilter() {
      public boolean accept(File file) {
        return !file.isDirectory() && StringUtil.endsWithIgnoreCase(file.getName(), XML_EXT);
      }
    });
    if (files == null) {
      LOG.error("Cannot read directory: " + colorsDir.getAbsolutePath());
      return new File[0];
    }
    return files;
  }

  private static File getColorsDir(boolean create) {
    @NonNls String directoryPath = PathManager.getConfigPath() + File.separator + "colors";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }


  public void addEditorColorsListener(EditorColorsListener listener) {
    myListeners.add(listener);
  }

  public void removeEditorColorsListener(EditorColorsListener listener) {
    myListeners.remove(listener);
  }

  public void setUseOnlyMonospacedFonts(boolean b) {
    USE_ONLY_MONOSPACED_FONTS = b;
  }

  public boolean isUseOnlyMonospacedFonts() {
    return USE_ONLY_MONOSPACED_FONTS;
  }

  public String getExternalFileName() {
    return "colors.scheme";
  }

  public File[] getExportFiles() {
    return new File[]{getColorsDir(true), PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return OptionsBundle.message("options.color.schemes.presentable.name");
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    Element element = parentNode.getChild(NODE_NAME);
    if (element != null) {
      String name = element.getAttributeValue(NAME_ATTR);
      if (name != null && !"".equals(name.trim())) {
        myGlobalSchemeName = name;
      }
    }

    initGlobalScheme();
  }

  private void initGlobalScheme() {
    if (myGlobalSchemeName != null) {
      setGlobalSchemeByName(myGlobalSchemeName);
    }
    else {
      setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
    }
  }

  private void setGlobalSchemeByName(String schemeName) {
    setGlobalScheme(mySchemesMap.get(schemeName));
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    if (myGlobalScheme != null) {
      Element element = new Element(NODE_NAME);
      element.setAttribute(NAME_ATTR, myGlobalScheme.getName());
      parentNode.addContent(element);
    }
  }

  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  public String getComponentName() {
    return "EditorColorsManagerImpl";
  }
}