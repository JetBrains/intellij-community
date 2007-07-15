package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectRootListener;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author mike
 */
public class ProjectRootContainerImpl implements JDOMExternalizable, ProjectRootContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private CompositeProjectRoot[] myRoots = new CompositeProjectRoot[ProjectRootType.ALL_TYPES.length];

  private VirtualFile[][] myFiles = new VirtualFile[ProjectRootType.ALL_TYPES.length][];

  private boolean myInsideChange = false;
  private List<ProjectRootListener> myListeners = new CopyOnWriteArrayList<ProjectRootListener>();

  private boolean myNoCopyJars = false;

  public ProjectRootContainerImpl(boolean noCopyJars) {
    myNoCopyJars = noCopyJars;

    for (int i = 0; i < myRoots.length; i++) {
      myRoots[i] = new CompositeProjectRoot();
    }
    for (int i = 0; i < myFiles.length; i++) {
      myFiles[i] = VirtualFile.EMPTY_ARRAY;
    }
  }

  public VirtualFile[] getRootFiles(ProjectRootType type) {
    return myFiles[type.getIndex()];
  }

  public ProjectRoot[] getRoots(ProjectRootType type) {
    return myRoots[type.getIndex()].getProjectRoots();
  }

  public void startChange() {
    LOG.assertTrue(!myInsideChange);

    myInsideChange = true;
  }

  public void finishChange() {
    LOG.assertTrue(myInsideChange);
    VirtualFile[][] oldRoots = new VirtualFile[ProjectRootType.ALL_TYPES.length][];
    for (int i = 0; i < oldRoots.length; i++) {
      oldRoots[i] = myFiles[i];
    }

    for (int i = 0; i < ProjectRootType.ALL_TYPES.length; i++) {
      final VirtualFile[] roots = myRoots[i].getVirtualFiles();
      final boolean same = Comparing.equal(roots, oldRoots[i]);

      myFiles[i] = myRoots[i].getVirtualFiles();

      if (!same) {
        fireRootsChanged(oldRoots[i], myFiles[i], ProjectRootType.ALL_TYPES[i]);
      }
    }

    myInsideChange = false;
  }

  public void addProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.add(listener);
  }

  public void removeProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.remove(listener);
  }

  private void fireRootsChanged(final VirtualFile[] oldRoots, final VirtualFile[] newRoots, final ProjectRootType type) {
    /*
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        LOG.info("roots changed: type='" + type + "'\n    oldRoots='" + Arrays.asList(oldRoots) + "'\n    newRoots='" + Arrays.asList(newRoots) + "' ");
      }
    });
    */
    for (final ProjectRootListener listener : myListeners) {
      listener.rootsChanged();
    }
  }


  public void removeRoot(ProjectRoot root, ProjectRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots[type.getIndex()].remove(root);
  }

  public ProjectRoot addRoot(VirtualFile virtualFile, ProjectRootType type) {
    LOG.assertTrue(myInsideChange);
    return myRoots[type.getIndex()].add(virtualFile);
  }

  public void addRoot(ProjectRoot root, ProjectRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots[type.getIndex()].add(root);
  }

  public void removeAllRoots(ProjectRootType type ) {
    LOG.assertTrue(myInsideChange);
    myRoots[type.getIndex()].clear();
  }

  public void removeRoot(VirtualFile root, ProjectRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots[type.getIndex()].remove(root);
  }

  public void removeAllRoots() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots) {
      myRoot.clear();
    }
  }

  public void update() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots) {
      myRoot.update();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (ProjectRootType type : ProjectRootType.ALL_TYPES) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFiles = new VirtualFile[ProjectRootType.ALL_TYPES.length][];
        for (int i = 0; i < myFiles.length; i++) {
          CompositeProjectRoot root = myRoots[i];
          if (myNoCopyJars){
            setNoCopyJars(root);
          }
          myFiles[i] = root.getVirtualFiles();
        }
      }
    });

    for (ProjectRootType type : ProjectRootType.ALL_TYPES) {
      final VirtualFile[] newRoots = getRootFiles(type);
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged(oldRoots, newRoots, type);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (ProjectRootType type : ProjectRootType.ALL_TYPES) {
      write(element, type);
    }
  }

  private static void setNoCopyJars(ProjectRoot root){
    if (root instanceof SimpleProjectRoot){
      String url = ((SimpleProjectRoot)root).getUrl();
      if (JarFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))){
        String path = VirtualFileManager.extractPath(url);
        JarFileSystem.getInstance().setNoCopyJarForPath(path);
      }
    }
    else if (root instanceof CompositeProjectRoot){
      ProjectRoot[] roots = ((CompositeProjectRoot)root).getProjectRoots();
      for (ProjectRoot root1 : roots) {
        setNoCopyJars(root1);
      }
    }
  }

  private void read(Element element, ProjectRootType type) throws InvalidDataException {
    Element child = element.getChild(ProjectRootUtil.typeToString(type));
    if (child == null){
      myRoots[type.getIndex()] = new CompositeProjectRoot();
      return;
    }

    List children = child.getChildren();
    LOG.assertTrue(children.size() == 1);
    myRoots[type.getIndex()] = (CompositeProjectRoot)ProjectRootUtil.read((Element)children.get(0));
  }

  private void write(Element roots, ProjectRootType type) throws WriteExternalException {
    Element e = new Element(ProjectRootUtil.typeToString(type));
    roots.addContent(e);
    final Element root = ProjectRootUtil.write(myRoots[type.getIndex()]);
    if (root != null) {
      e.addContent(root);
    }
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  void readOldVersion(Element child) {
    for (final Object o : child.getChildren("root")) {
      Element root = (Element)o;
      String url = root.getAttributeValue("file");
      SimpleProjectRoot projectRoot = new SimpleProjectRoot(url);
      String type = root.getChild("property").getAttributeValue("value");

      if (type.equals("sourcePathEntry")) {
        addRoot(projectRoot, ProjectRootType.SOURCE);
      }
      else if (type.equals("javadocPathEntry")) {
        addRoot(projectRoot, ProjectRootType.JAVADOC);
      }
      else if (type.equals("classPathEntry")) {
        addRoot(projectRoot, ProjectRootType.CLASS);
      }
    }

    myFiles = new VirtualFile[ProjectRootType.ALL_TYPES.length][];
    for (int i = 0; i < myFiles.length; i++) {
      myFiles[i] = myRoots[i].getVirtualFiles();
    }
    for (ProjectRootType type : ProjectRootType.ALL_TYPES) {
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      final VirtualFile[] newRoots = getRootFiles(type);
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged(oldRoots, newRoots, type);
      }
    }
  }

}
