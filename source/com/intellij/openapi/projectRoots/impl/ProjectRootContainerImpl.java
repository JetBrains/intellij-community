package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectRootListener;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author mike
 */
public class ProjectRootContainerImpl implements JDOMExternalizable, ProjectRootContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private Map<OrderRootType, CompositeProjectRoot> myRoots = new HashMap<OrderRootType, CompositeProjectRoot>();

  private Map<OrderRootType, VirtualFile[]> myFiles = new HashMap<OrderRootType, VirtualFile[]>();

  private boolean myInsideChange = false;
  private List<ProjectRootListener> myListeners = new CopyOnWriteArrayList<ProjectRootListener>();

  private boolean myNoCopyJars = false;

  public ProjectRootContainerImpl(boolean noCopyJars) {
    myNoCopyJars = noCopyJars;

    for(OrderRootType rootType: OrderRootType.getAllTypes()) {
      myRoots.put(rootType, new CompositeProjectRoot());
      myFiles.put(rootType, VirtualFile.EMPTY_ARRAY);
    }
  }

  @NotNull
  public VirtualFile[] getRootFiles(OrderRootType type) {
    return myFiles.get(type);
  }

  public ProjectRoot[] getRoots(OrderRootType type) {
    return myRoots.get(type).getProjectRoots();
  }

  public void startChange() {
    LOG.assertTrue(!myInsideChange);

    myInsideChange = true;
  }

  public void finishChange() {
    LOG.assertTrue(myInsideChange);
    HashMap<OrderRootType, VirtualFile[]> oldRoots = new HashMap<OrderRootType, VirtualFile[]>(myFiles);

    for (OrderRootType orderRootType: OrderRootType.getAllTypes()) {
      final VirtualFile[] roots = myRoots.get(orderRootType).getVirtualFiles();
      final boolean same = Comparing.equal(roots, oldRoots.get(orderRootType));

      myFiles.put(orderRootType, myRoots.get(orderRootType).getVirtualFiles());

      if (!same) {
        fireRootsChanged();
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

  private void fireRootsChanged() {
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


  public void removeRoot(ProjectRoot root, OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  public ProjectRoot addRoot(VirtualFile virtualFile, OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    return myRoots.get(type).add(virtualFile);
  }

  public void addRoot(ProjectRoot root, OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).add(root);
  }

  public void removeAllRoots(OrderRootType type ) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).clear();
  }

  public void removeRoot(VirtualFile root, OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  public void removeAllRoots() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.clear();
    }
  }

  public void update() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.update();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFiles = new HashMap<OrderRootType, VirtualFile[]>();
        for(OrderRootType rootType: myRoots.keySet()) {
          CompositeProjectRoot root = myRoots.get(rootType);
          if (myNoCopyJars){
            setNoCopyJars(root);
          }
          myFiles.put(rootType, root.getVirtualFiles());
        }
      }
    });

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] newRoots = getRootFiles(type);
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    List<OrderRootType> allTypes = OrderRootType.getSortedRootTypes();
    for (OrderRootType type : allTypes) {
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

  private void read(Element element, OrderRootType type) throws InvalidDataException {
    Element child = element.getChild(type.getSdkRootName());
    if (child == null){
      myRoots.put(type, new CompositeProjectRoot());
      return;
    }

    List children = child.getChildren();
    LOG.assertTrue(children.size() == 1);
    myRoots.put(type, (CompositeProjectRoot)ProjectRootUtil.read((Element)children.get(0)));
  }

  private void write(Element roots, OrderRootType type) throws WriteExternalException {
    Element e = new Element(type.getSdkRootName());
    roots.addContent(e);
    final Element root = ProjectRootUtil.write(myRoots.get(type));
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
        addRoot(projectRoot, OrderRootType.SOURCES);
      }
      else if (type.equals("javadocPathEntry")) {
        addRoot(projectRoot, JavadocOrderRootType.INSTANCE);
      }
      else if (type.equals("classPathEntry")) {
        addRoot(projectRoot, OrderRootType.CLASSES);
      }
    }

    myFiles = new HashMap<OrderRootType, VirtualFile[]>();
    for(OrderRootType rootType: myRoots.keySet()) {
      myFiles.put(rootType, myRoots.get(rootType).getVirtualFiles());
    }
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      final VirtualFile[] newRoots = getRootFiles(type);
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
      }
    }
  }

}
