package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class ModuleJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements WritableOrderEntry,
                                                                                  ClonableOrderEntry,
                                                                                  ModuleJdkOrderEntry,
                                                                                  ProjectJdkTable.Listener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.JdkLibraryEntryImpl");
  @NonNls static final String ENTRY_TYPE = "jdk";
  @NonNls private static final String JDK_NAME_ATTR = "jdkName";
  @NonNls private static final String JDK_TYPE_ATTR = "jdkType";

  private ProjectJdk myJdk;
  private String myJdkName;
  private String myJdkType;

  ModuleJdkOrderEntryImpl(ProjectJdk projectJdk,
                          RootModelImpl rootModel,
                          ProjectRootManagerImpl projectRootManager,
                          VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    LOG.assertTrue(projectJdk != null);
    myJdk = projectJdk;
    setJdkName(null);
    setJdkType(null);
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
    final String jdkType = element.getAttributeValue(JDK_TYPE_ATTR);
    final ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    final ProjectJdk jdkByName = projectJdkTable.findJdk(jdkName, jdkType);
    if (jdkByName == null) {
      myJdk = null;
      setJdkName(jdkName);
      setJdkType(jdkType);
    }
    else {
      myJdk = jdkByName;
      setJdkName(null);
      setJdkType(null);
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
    setJdkType(that.getJdkType());
    init(getRootProvider());
    addListener();
  }

  private String getJdkType() {
    if (myJdk != null){
      return myJdk.getSdkType().getName();
    }
    return myJdkType;
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
    if (myRootModel.isWritable() && !ApplicationManager.getApplication().isUnitTestMode()) {
      final Project project = myRootModel.getModule().getProject();
      return ProjectJdksModel.getInstance(project).getProjectJdks().get(myJdk);
    }

    return myJdk;
  }

  public String getJdkName() {
    ProjectJdk jdk = getJdk();
    if (jdk != null) {
      return jdk.getName();
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
    return !isDisposed() && getJdk() != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleJdkOrderEntry(this, initialValue);
  }

  public void jdkAdded(ProjectJdk jdk) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkNameChanged(ProjectJdk jdk, String previousName) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkRemoved(ProjectJdk jdk) {
    if (jdk == myJdk) {
      setJdkName(myJdk.getName());
      setJdkType(myJdk.getSdkType().getName());
      myJdk = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String jdkName = getJdkName();
    if (jdkName != null) {
      element.setAttribute(JDK_NAME_ATTR, jdkName);
    }
    final String jdkType = getJdkType();
    if (jdkType != null) {
      element.setAttribute(JDK_TYPE_ATTR, jdkType);
    }
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

  private void setJdkType(String jdkType) {
    myJdkType = jdkType;
  }
}
