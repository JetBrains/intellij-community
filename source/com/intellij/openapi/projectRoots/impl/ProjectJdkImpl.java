package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.Disposable;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class ProjectJdkImpl implements JDOMExternalizable, ProjectJdk, SdkModificator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectJdkImpl");
  private final ProjectRootContainerImpl myRootContainer;
  private String myName;
  private String myVersionString;
  private boolean myVersionDefined = false;
  private String myHomePath = "";
  private final MyRootProvider myRootProvider = new MyRootProvider();
  private ProjectJdkImpl myOrigin = null;
  private SdkAdditionalData myAdditionalData = null;
  private SdkType mySdkType;
  @NonNls private static final String ELEMENT_NAME = "name";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String ELEMENT_TYPE = "type";
  @NonNls private static final String ELEMENT_VERSION = "version";
  @NonNls private static final String ELEMENT_ROOTS = "roots";
  @NonNls private static final String ELEMENT_ROOT = "root";
  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String VALUE_JDKHOME = "jdkHome";
  @NonNls private static final String ATTRIBUTE_FILE = "file";
  @NonNls private static final String ELEMENT_HOMEPATH = "homePath";
  @NonNls private static final String ELEMENT_ADDITIONAL = "additional";

  public ProjectJdkImpl(String name, SdkType sdkType) {
    mySdkType = sdkType;
    myRootContainer = new ProjectRootContainerImpl(true);
    myName = name;
    myRootContainer.addProjectRootContainerListener(myRootProvider);
  }

  public SdkType getSdkType() {
    return mySdkType;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    LOG.assertTrue(name != null);
    myName = name;
  }

  public final void setVersionString(String versionString) {
    myVersionString = (versionString == null || "".equals(versionString)) ? null : versionString;
    myVersionDefined = true;
  }

  public String getVersionString() {
    if (myVersionString == null && !myVersionDefined) {
      String homePath = getHomePath();
      if (homePath != null && homePath.length() > 0) {
        setVersionString(mySdkType.getVersionString(homePath));
      }
    }
    return myVersionString;
  }

  public String getHomePath() {
    return myHomePath;
  }

  public VirtualFile getHomeDirectory() {
    if (myHomePath == null) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(myHomePath);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName = element.getChild(ELEMENT_NAME).getAttributeValue(ATTRIBUTE_VALUE);
    final Element typeChild = element.getChild(ELEMENT_TYPE);
    final String sdkTypeName = typeChild != null? typeChild.getAttributeValue(ATTRIBUTE_VALUE) : null;
    if (sdkTypeName != null) {
      mySdkType = getSdkTypeByName(sdkTypeName);
    }
    else {
      // assume java sdk by default
      mySdkType = JavaSdk.getInstance();
    }
    final Element version = element.getChild(ELEMENT_VERSION);
    setVersionString((version != null) ? version.getAttributeValue(ATTRIBUTE_VALUE) : null);

    if (element.getAttribute(ELEMENT_VERSION) == null || !"2".equals(element.getAttributeValue(ELEMENT_VERSION))) {
      myRootContainer.startChange();
      myRootContainer.readOldVersion(element.getChild(ELEMENT_ROOTS));
      final List children = element.getChild(ELEMENT_ROOTS).getChildren(ELEMENT_ROOT);
      for (final Object aChildren : children) {
        Element root = (Element)aChildren;
        for (final Object o : root.getChildren(ELEMENT_PROPERTY)) {
          Element prop = (Element)o;
          if (ELEMENT_TYPE.equals(prop.getAttributeValue(ELEMENT_NAME)) && VALUE_JDKHOME.equals(prop.getAttributeValue(ATTRIBUTE_VALUE))) {
            myHomePath = VirtualFileManager.extractPath(root.getAttributeValue(ATTRIBUTE_FILE));
          }
        }
      }
      myRootContainer.finishChange();
    }
    else {
      myHomePath = element.getChild(ELEMENT_HOMEPATH).getAttributeValue(ATTRIBUTE_VALUE);
      myRootContainer.readExternal(element.getChild(ELEMENT_ROOTS));
    }

    final Element additional = element.getChild(ELEMENT_ADDITIONAL);
    myAdditionalData = (additional != null)? mySdkType.loadAdditionalData(additional) : null;
  }

  private static SdkType getSdkTypeByName(String sdkTypeName) {
    final SdkType[] allSdkTypes = ApplicationManager.getApplication().getComponents(SdkType.class);
    for (final SdkType type : allSdkTypes) {
      if (type.getName().equals(sdkTypeName)) {
        return type;
      }
    }
    return UnknownSdkType.getInstance(sdkTypeName);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ELEMENT_VERSION, "2");

    final Element name = new Element(ELEMENT_NAME);
    name.setAttribute(ATTRIBUTE_VALUE, myName);
    element.addContent(name);

    if (mySdkType != null) {
      final Element sdkType = new Element(ELEMENT_TYPE);
      sdkType.setAttribute(ATTRIBUTE_VALUE, mySdkType.getName());
      element.addContent(sdkType);
    }

    if (myVersionString != null) {
      final Element version = new Element(ELEMENT_VERSION);
      version.setAttribute(ATTRIBUTE_VALUE, myVersionString);
      element.addContent(version);
    }

    final Element home = new Element(ELEMENT_HOMEPATH);
    home.setAttribute(ATTRIBUTE_VALUE, myHomePath);
    element.addContent(home);

    Element roots = new Element(ELEMENT_ROOTS);
    myRootContainer.writeExternal(roots);
    element.addContent(roots);

    Element additional = new Element(ELEMENT_ADDITIONAL);
    if (myAdditionalData != null) {
      mySdkType.saveAdditionalData(myAdditionalData, additional);
    }
    element.addContent(additional);
  }

  public void setHomePath(String path) {
    final boolean changes = myHomePath == null? path != null : !myHomePath.equals(path);
    myHomePath = path;
    if (changes) {
      myVersionString = null; // clear cached value if home path changed
      myVersionDefined = false;
    }
  }

  public final String getBinPath() {
    return mySdkType.getBinPath(this);
  }

  public final String getToolsPath() {
    return mySdkType.getToolsPath(this);
  }

  public final String getVMExecutablePath() {
    return mySdkType.getVMExecutablePath(this);
  }

  public final String getRtLibraryPath() {
    return mySdkType.getRtLibraryPath(this);
  }

  public Object clone() throws CloneNotSupportedException {
    ProjectJdkImpl newJdk = new ProjectJdkImpl("", mySdkType);
    copyTo(newJdk);
    return newJdk;
  }

  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  public void copyTo(ProjectJdkImpl dest){
    final String previousName = dest.getName();
    final String name = getName();
    dest.setName(name);
    dest.setHomePath(getHomePath());
    dest.setVersionString(getVersionString());
    dest.setSdkAdditionalData(getSdkAdditionalData());
    dest.myRootContainer.startChange();
    dest.myRootContainer.removeAllRoots();
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.CLASS);
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.SOURCE);
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.JAVADOC);
    dest.myRootContainer.finishChange();
  }

  private static void copyRoots(ProjectRootContainer srcContainer, ProjectRootContainer destContainer, ProjectRootType type){
    final ProjectRoot[] newRoots = srcContainer.getRoots(type);
    for (ProjectRoot newRoot : newRoots) {
      destContainer.addRoot(newRoot, type);
    }
  }

  private class MyRootProvider extends RootProviderBaseImpl implements ProjectRootListener {
    public String[] getUrls(OrderRootType rootType) {
      final VirtualFile[] rootFiles = myRootContainer.getRootFiles(ourOrderRootsToProjectRoots.get(rootType));
      final ArrayList<String> result = new ArrayList<String>();
      for (VirtualFile rootFile : rootFiles) {
        result.add(rootFile.getUrl());
      }
      return result.toArray(new String[result.size()]);
    }

    private Set<RootSetChangedListener> myListeners = new HashSet<RootSetChangedListener>();

    public void addRootSetChangedListener(RootSetChangedListener listener) {
      synchronized (this) {
        myListeners.add(listener);
      }
      super.addRootSetChangedListener(listener);
    }

    public void addRootSetChangedListener(final RootSetChangedListener listener, Disposable parentDisposable) {
      addRootSetChangedListener(listener, parentDisposable);
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          removeRootSetChangedListener(listener);
        }
      });
    }

    public void removeRootSetChangedListener(RootSetChangedListener listener) {
      super.removeRootSetChangedListener(listener);
      synchronized (this) {
        myListeners.remove(listener);
      }
    }

    public void rootsChanged() {
      synchronized (this) {
        if (myListeners.size() == 0) {
          return;
        }
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fireRootSetChanged();
        }
      });
    }
  }

  private final static HashMap<OrderRootType,ProjectRootType> ourOrderRootsToProjectRoots = new HashMap<OrderRootType, ProjectRootType>();

  static {
    ourOrderRootsToProjectRoots.put(OrderRootType.CLASSES, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.CLASSES_AND_OUTPUT, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.COMPILATION_CLASSES, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.SOURCES, ProjectRootType.SOURCE);
    ourOrderRootsToProjectRoots.put(OrderRootType.JAVADOC, ProjectRootType.JAVADOC);
  }

  // SdkModificator implementation

  public SdkModificator getSdkModificator() {
    try {
      ProjectJdkImpl sdk = (ProjectJdkImpl)clone();
      sdk.myOrigin = this;
      sdk.myRootContainer.startChange();
      sdk.update();
      return sdk;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e); // should not happen
      return null;
    }
  }

  public void commitChanges() {
    LOG.assertTrue(isWritable());
    myRootContainer.finishChange();
    copyTo(myOrigin);
    myOrigin = null;
  }

  public SdkAdditionalData getSdkAdditionalData() {
    return myAdditionalData;
  }

  public void setSdkAdditionalData(SdkAdditionalData data) {
    myAdditionalData = data;
  }

  public VirtualFile[] getRoots(ProjectRootType rootType) {
    final ProjectRoot[] roots = myRootContainer.getRoots(rootType); // use getRoots() cause the data is most up-to-date there
    final List<VirtualFile> files = new ArrayList<VirtualFile>(roots.length);
    for (ProjectRoot root : roots) {
      files.addAll(Arrays.asList(root.getVirtualFiles()));
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public void addRoot(VirtualFile root, ProjectRootType rootType) {
    myRootContainer.addRoot(root, rootType);
  }

  public void removeRoot(VirtualFile root, ProjectRootType rootType) {
    myRootContainer.removeRoot(root, rootType);
  }

  public void removeRoots(ProjectRootType rootType) {
    myRootContainer.removeAllRoots(rootType);
  }

  public void removeAllRoots() {
    myRootContainer.removeAllRoots();
  }

  public boolean isWritable() {
    return myOrigin != null;
  }

  public void update() {
    myRootContainer.update();
  }
}