package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * @author dsl
 */
public class ModuleJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements WritableOrderEntry,
                                                                                  ClonableOrderEntry,
                                                                                  ModuleJdkOrderEntry,
                                                                                  ProjectJdkTable.Listener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.JdkLibraryEntryImpl");
  static final String ENTRY_TYPE = "jdk";
  private static final String JDK_NAME_ATTR = "jdkName";

  private ProjectJdk myJdk;
  private String myJdkName;

  ModuleJdkOrderEntryImpl(ProjectJdk projectJdk,
                          RootModelImpl rootModel,
                          ProjectRootManagerImpl projectRootManager,
                          VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    LOG.assertTrue(projectJdk != null);
    myJdk = projectJdk;
    setJdkName(null);
    init(getRootProvider());
    addListener();
  }

  ModuleJdkOrderEntryImpl(Element element,
                          RootModelImpl rootModel,
                          ProjectRootManagerImpl projectRootManager,
                          VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    super(rootModel, projectRootManager, filePointerManager);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
    final Attribute jdkNameAttribute = element.getAttribute(JDK_NAME_ATTR);
    if (jdkNameAttribute == null) {
      throw new InvalidDataException();
    }

    final String jdkName = jdkNameAttribute.getValue();
    final ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    final ProjectJdk jdkByName = projectJdkTable.findJdk(jdkName);
    if (jdkByName == null) {
      myJdk = null;
      setJdkName(jdkName);
    }
    else {
      myJdk = jdkByName;
      setJdkName(null);
    }
    init(getRootProvider());
    addListener();
  }

  private ModuleJdkOrderEntryImpl(ModuleJdkOrderEntryImpl that,
                                  RootModelImpl rootModel,
                                  ProjectRootManagerImpl projectRootManager,
                                  VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    myJdk = that.myJdk;
    setJdkName(that.getJdkName());
    init(getRootProvider());
    addListener();
  }

  private void addListener() {
    myProjectRootManagerImpl.addJdkTableListener(this);
  }

  private RootProvider getRootProvider() {
    if (myJdk != null) {
      return myJdk.getRootProvider();
    }
    else {
      return null;
    }
  }

  public ProjectJdk getJdk() {
    return myJdk;
  }

  public String getJdkName() {
    if (myJdk != null) {
      return myJdk.getName();
    }
    else {
      return myJdkName;
    }
  }

  public boolean isSynthetic() {
    return true;
  }


  public String getPresentableName() {
    if (myJdk != null) {
      return "< " + myJdk.getName() + " >";
    }
    else {
      return "< " + getJdkName() + " >";
    }
  }

  public boolean isValid() {
    return myJdk != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleJdkOrderEntry(this, initialValue);
  }

  public void jdkAdded(ProjectJdk jdk) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      myJdkName = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkNameChanged(ProjectJdk jdk, String previousName) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      myJdkName = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkRemoved(ProjectJdk jdk) {
    if (jdk == myJdk) {
      setJdkName(myJdk.getName());
      myJdk = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(JDK_NAME_ATTR, myJdk != null ? myJdk.getName() : getJdkName());
    rootElement.addContent(element);
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleJdkOrderEntryImpl(this, rootModel, ProjectRootManagerImpl.getInstanceImpl(myRootModel.getModule().getProject()),
                                       VirtualFilePointerManager.getInstance());
  }

  protected void dispose() {
    super.dispose();
    myProjectRootManagerImpl.removeJdkTableListener(this);
  }

  private void setJdkName(String jdkName) {
    myJdkName = jdkName;
  }

}
