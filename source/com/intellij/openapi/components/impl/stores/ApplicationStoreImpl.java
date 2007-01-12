package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ApplicationStoreImpl extends ComponentStoreImpl implements IApplicationStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ApplicationStoreImpl");

  @NonNls private static final String APPLICATION_ELEMENT = "application";
  @NonNls private static final String ELEMENT_COMPONENT = "component";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  @NonNls private static final String XML_EXTENSION = ".xml";

  private ApplicationImpl myApplication;


  public ApplicationStoreImpl(final ApplicationImpl application) {
    myApplication = application;
  }

  public void initStore() {
  }

  private synchronized void loadConfiguration(String path) {
    clearDomMap();

    File configurationDir = new File(path);
    if (!configurationDir.exists()) return;

    Set<String> names = new HashSet<String>(Arrays.asList(configurationDir.list()));

    for (Iterator<String> i = names.iterator(); i.hasNext();) {
      String name = i.next();
      if (name.endsWith(XML_EXTENSION)) {
        String backupName = name + "~";
        if (names.contains(backupName)) i.remove();
      }
    }

    for (String name : names) {
      if (!name.endsWith(XML_EXTENSION) && !name.endsWith(XML_EXTENSION + "~")) continue; // see SCR #12791
      final String filePath = path + File.separatorChar + name;
      File file = new File(filePath);
      if (!file.exists() || !file.isFile()) continue;

      try {
        loadFile(filePath);
      }
      catch (Exception e) {
        //OK here. Just drop corrupted settings.
      }
    }
  }

  private synchronized void loadFile(String filePath) throws JDOMException, InvalidDataException, IOException {
    Document document = JDOMUtil.loadDocument(new File(filePath));
    if (document == null) {
      throw new InvalidDataException();
    }

    Element root = document.getRootElement();
    if (root == null || !APPLICATION_ELEMENT.equals(root.getName())) {
      throw new InvalidDataException();
    }

    final List<String> additionalFiles = new ArrayList<String>();
    List children = root.getChildren(ELEMENT_COMPONENT);
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;

      String name = element.getAttributeValue(ATTRIBUTE_NAME);
      if (name == null || name.length() == 0) {
        String className = element.getAttributeValue(ATTRIBUTE_CLASS);
        if (className == null) {
          throw new InvalidDataException();
        }
        name = className.substring(className.lastIndexOf('.') + 1);
      }

      convertComponents(root, filePath, additionalFiles);

      addConfiguration(name, element);
    }

    for (String additionalPath : additionalFiles) {
      loadFile(additionalPath);
    }
  }

  private static void convertComponents(Element root, String filePath, final List<String> additionalFiles) {// Converting components
    final String additionalFilePath;
    additionalFilePath = Convertor34.convertLibraryTable34(root, filePath);
    if (additionalFilePath != null) {
      additionalFiles.add(additionalFilePath);
    }
    // Additional converors here probably, adding new files to load
    // to aditionalFiles
  }

  public synchronized void loadApplication(final String path) {
    try {
      if (path == null) return;
      loadConfiguration(path);
    }
    finally {
      myApplication.initComponents();
      clearDomMap();
    }
  }

  public synchronized void saveApplication(String path) throws IOException {
    deleteBackupFiles(path);
    backupFiles(path);

    Class[] componentClasses = myApplication.getComponentInterfaces();

    HashMap<String, Element> fileNameToRootElementMap = new HashMap<String, Element>();

    for (Class<?> componentClass : componentClasses) {
      Object component = myApplication.getComponent(componentClass);
      if (!(component instanceof BaseComponent)) continue;
      String fileName;
      if (component instanceof NamedJDOMExternalizable) {
        fileName = ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
      }
      else {
        fileName = PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;
      }

      Element root = getRootElement(fileNameToRootElementMap, fileName);
      try {
        Element node = serializeComponent(component);
        if (node != null) {
          root.addContent(node);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    for (String fileName : fileNameToRootElementMap.keySet()) {
      Element root = fileNameToRootElementMap.get(fileName);

      JDOMUtil.writeDocument(new Document(root), path + File.separatorChar + fileName,
                             CodeStyleSettingsManager.getSettings(null).getLineSeparator());
    }

    deleteBackupFiles(path);
  }

  private static void backupFiles(String path) throws IOException {
    String[] list = new File(path).list();
    for (String name : list) {
      if (StringUtil.endsWithIgnoreCase(name, XML_EXTENSION)) {
        File file = new File(path + File.separatorChar + name);
        File newFile = new File(path + File.separatorChar + name + "~");
        FileUtil.rename(file, newFile);
      }
    }
  }

  private static Element getRootElement(Map<String, Element> fileNameToRootElementMap, String fileName) {
    Element root = fileNameToRootElementMap.get(fileName);
    if (root == null) {
      root = new Element(APPLICATION_ELEMENT);
      fileNameToRootElementMap.put(fileName, root);
    }
    return root;
  }

  private static void deleteBackupFiles(String path) throws IOException {
    String[] list = new File(path).list();
    for (String name : list) {
      if (StringUtil.endsWithChar(name.toLowerCase(), '~')) {
        File file = new File(path + File.separatorChar + name);
        if (!file.delete()) {
          throw new IOException(ApplicationBundle.message("backup.cannot.delete.file", file.getPath()));
        }
      }
    }
  }

  protected Element getDefaults(final Object component) throws JDOMException, IOException, InvalidDataException {
    InputStream stream = getDefaultsInputStream(component);

    if (stream != null) {
      Document document = null;
      try {
        document = JDOMUtil.loadDocument(stream);
      }
      finally {
        stream.close();
      }
      if (document == null) {
        throw new InvalidDataException();
      }
      Element root = document.getRootElement();
      if (root == null || !ELEMENT_COMPONENT.equals(root.getName())) {
        throw new InvalidDataException();
      }
      return root;
    }
    return null;
  }

  @Nullable
  private static InputStream getDefaultsInputStream(Object component) {
    return DecodeDefaultsUtil.getDefaultsInputStream(component);
  }

}
