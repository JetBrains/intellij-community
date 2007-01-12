package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.project.impl.convertors.Convertor01;
import com.intellij.openapi.project.impl.convertors.Convertor12;
import com.intellij.openapi.project.impl.convertors.Convertor23;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.BaseFileConfigurableStoreImpl");

  private int myOriginalVersion = -1;
  private boolean mySavePathsRelative;
  private final HashMap<String,String> myConfigurationNameToFileName = new HashMap<String,String>();
  @NonNls private static final String RELATIVE_PATHS_OPTION = "relativePaths";
  @NonNls private static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  @NonNls static final String ELEMENT_COMPONENT = "component";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  private ComponentManagerImpl myComponentManager;
  private static ArrayList<String> ourConversionProblemsStorage = new ArrayList<String>();

  @Nullable
  protected abstract VirtualFile getComponentConfigurationFile(Class componentInterface);
  protected abstract String getRootNodeName();
  protected abstract ConfigurationFile[] getConfigurationFiles();


  protected BaseFileConfigurableStoreImpl(final ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
  }

  public synchronized ComponentManagerImpl getComponentManager() {
    return myComponentManager;
  }

  public synchronized Element saveToXml(Element targetRoot, VirtualFile configFile) {
    String filePath = configFile != null ? configFile.getPath() : null;
    Element root;
    if (targetRoot != null) {
      root = targetRoot;
    }
    else {
      root = new Element(getRootNodeName());
    }

    root.setAttribute(VERSION_OPTION, Integer.toString(ProjectManagerImpl.CURRENT_FORMAT_VERSION));
    root.setAttribute(RELATIVE_PATHS_OPTION, Boolean.toString(isSavePathsRelative()));

    List<Element> newContents = new ArrayList<Element>();

    if (targetRoot == null) {
      //save configuration without components
      final Set<String> result;
      result = getConfigurationNames();
      final Set<String> names = result;
      for (String name : names) {
        if (Comparing.equal(myConfigurationNameToFileName.get(name), filePath)) {
          final Element result1;
          result1 = getConfiguration(name);
          final Element e = result1;
          if (e != null) {
            newContents.add((Element)e.clone());
          }
        }
      }
    }

    final Class[] componentInterfaces = myComponentManager.getComponentInterfaces();
    for (Class<?> componentInterface : componentInterfaces) {
      VirtualFile componentFile = getComponentConfigurationFile(componentInterface);

      if (configFile == componentFile) {
        final Object component = myComponentManager.getComponent(componentInterface);
        if (!(component instanceof BaseComponent)) continue;
        BaseComponent baseComponent = (BaseComponent)component;

        try {
          final Element node = serializeComponent(baseComponent);
          if (node != null) {
            newContents.add(node);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }

    Collections.sort(newContents, new Comparator<Element>() {
      public int compare(Element e1, Element e2) {
        String name1 = e1.getAttributeValue(ATTRIBUTE_NAME);
        String name2 = e2.getAttributeValue(ATTRIBUTE_NAME);
        if (name2 == null && name1 == null) return 0;
        if (name1 == null) return 1;
        if (name2 == null) return -1;
        return name1.compareTo(name2);
      }
    });

    for (final Element newContent : newContents) {
      root.addContent(newContent);
    }

    return root;
  }


  public synchronized void loadFromXml(Element root, String filePath) throws InvalidDataException {
    getExpandMacroReplacements().substitute(root, SystemInfo.isFileSystemCaseSensitive);

    int originalVersion = 0;
    try {
      originalVersion = Integer.parseInt(root.getAttributeValue(VERSION_OPTION));
    }
    catch (NumberFormatException e) {
      LOG.info(e);
    }
    if (originalVersion < 1) {
      Convertor01.execute(root);
    }
    if (originalVersion < 2) {
      Convertor12.execute(root);
    }
    if (originalVersion < 3) {
      Convertor23.execute(root);
    }
    if (originalVersion < 4) {
      Convertor34.execute(root, filePath, getConversionProblemsStorage());
    }

    if (getOriginalVersion() == -1) myOriginalVersion = originalVersion;
    myOriginalVersion = Math.min(getOriginalVersion(), originalVersion);

    String relative = root.getAttributeValue(RELATIVE_PATHS_OPTION);
    if (relative != null) {
      setSavePathsRelative(Boolean.parseBoolean(relative));
    }

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

      addConfiguration(name, element);
      myConfigurationNameToFileName.put(name, filePath);
    }

  }

  ExpandMacroToPathMap getExpandMacroReplacements() {
    return PathMacroManager.getInstance(myComponentManager).getExpandMacroMap();
  }


  ReplacePathToMacroMap getMacroReplacements() {
    return PathMacroManager.getInstance(myComponentManager).getReplacePathMap();
  }

  @Nullable
  static ArrayList<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  synchronized int getOriginalVersion() {
    return myOriginalVersion;
  }

  synchronized void save() throws IOException {
    for (ConfigurationFile file : getConfigurationFiles()) {
      final VirtualFile vFile = file.getVirtualFile();
      file.save(saveToXml(null, vFile), getMacroReplacements(), getLineSeparator(vFile));
    }
  }

  final void collectReadonlyFiles(final ConfigurationFile[] files, final List<VirtualFile> readonlyFiles) {
    if (files != null) {
      final ReplacePathToMacroMap replacements = getMacroReplacements();
      for (ConfigurationFile file : files) {
        final VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && !vFile.isWritable() && configFileNeedsToBeWritten(file, replacements)) {
          readonlyFiles.add(vFile);
        }
      }
    }
  }

  private boolean configFileNeedsToBeWritten(ConfigurationFile file, final ReplacePathToMacroMap replacements) {
    final VirtualFile vFile = file.getVirtualFile();
    return !ProjectManagerEx.getInstanceEx().isFileSavedToBeReloaded(vFile) &&
           file.needsSave(saveToXml(null, vFile), replacements, getLineSeparator(vFile));
  }

  synchronized String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, null);
  }

  public synchronized void loadSavedConfiguration() throws JDOMException, IOException, InvalidDataException {
    clearDomMap();

    ConfigurationFile[] configurationFiles = getConfigurationFiles();
    for (ConfigurationFile configurationFile : configurationFiles) {
      VirtualFile vFile = configurationFile.getVirtualFile();
      if (vFile != null) {
        loadFromFile(vFile);
      }
    }
  }

  private void loadFromFile(VirtualFile file) throws JDOMException, InvalidDataException, IOException {
    final CharSequence text = FileDocumentManager.getInstance().getDocument(file).getCharsSequence();
    Document document = JDOMUtil.loadDocument(CharArrayUtil.fromSequence(text), text.length());
    Element root = document.getRootElement();
    if (root == null) {
      throw new InvalidDataException();
    }
    loadFromXml(root, file.getPath());
  }

  synchronized boolean isSavePathsRelative() {
    return mySavePathsRelative;
  }

  public synchronized void setSavePathsRelative(boolean b) {
    mySavePathsRelative = b;
  }

}
