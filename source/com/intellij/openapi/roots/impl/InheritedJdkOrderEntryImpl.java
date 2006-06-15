package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.InheritedJdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class InheritedJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements InheritedJdkOrderEntry, ClonableOrderEntry,
                                                                                     WritableOrderEntry {
  private final ProjectRootManagerEx myProjectRootManager;
  @NonNls static final String ENTRY_TYPE = "inheritedJdk";
  private final MyJdkTableListener myJdkTableListener = new MyJdkTableListener();
  private final MyProjectJdkListener myListener = new MyProjectJdkListener();

  InheritedJdkOrderEntryImpl(RootModelImpl rootModel,
                             ProjectRootManagerImpl projectRootManager,
                             VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    myProjectRootManager = projectRootManager;
    myProjectRootManager.addProjectJdkListener(myListener);
    init(getRootProvider());
    myProjectRootManagerImpl.addJdkTableListener(myJdkTableListener);
  }


  /**
   * @param element
   * @param rootModel
   * @param projectRootManager
   * @param filePointerManager
   * @throws InvalidDataException
   */
  InheritedJdkOrderEntryImpl(Element element,
                             RootModelImpl rootModel,
                             ProjectRootManagerImpl projectRootManager,
                             VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    super(rootModel, projectRootManager, filePointerManager);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
    myProjectRootManager = projectRootManager;
    myProjectRootManager.addProjectJdkListener(myListener);
    init(getRootProvider());
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new InheritedJdkOrderEntryImpl(rootModel, projectRootManager, filePointerManager);
  }

  public boolean isSynthetic() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitInheritedJdkOrderEntry(this, initialValue);
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element orderEntryElement = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    rootElement.addContent(orderEntryElement);
  }

  public ProjectJdk getJdk() {
    if (!getRootModel().isWritable()){
      return myProjectRootManager.getProjectJdk();
    }
    final Project project = getRootModel().getModule().getProject();
    return ProjectRootConfigurable.getInstance(project).getProjectJdksModel().getProjectJdk();
  }

  public String getJdkName() {
    if (!getRootModel().isWritable() || ApplicationManager.getApplication().isUnitTestMode()){
      myProjectRootManager.getProjectJdkName();
    }
    final ProjectJdk projectJdk = getJdk();
    return projectJdk != null ? projectJdk.getName() : null;
  }

  private RootProvider getRootProvider() {
    final ProjectJdk projectJdk = myProjectRootManager.getProjectJdk();
    if (projectJdk != null) {
      return projectJdk.getRootProvider();
    }
    else {
      return null;
    }
  }


  public String getPresentableName() {
    return "< " + getJdkName() + " >";
  }

  protected void dispose() {
    super.dispose();
    myProjectRootManagerImpl.removeJdkTableListener(myJdkTableListener);
    myProjectRootManager.removeProjectJdkListener(myListener);
  }


  private class MyJdkTableListener implements ProjectJdkTable.Listener {
    public void jdkRemoved(ProjectJdk jdk) {
      if (isAffectedByJdkAddition(jdk)) {
        updateFromRootProviderAndSubscribe(null);
      }
    }

    private boolean isAffectedByJdkAddition(ProjectJdk jdk) {
      return jdk.equals(getJdk());
    }

    public void jdkAdded(ProjectJdk jdk) {
      if (isAffectedByJdkRemoval(jdk)) {
        updateFromRootProviderAndSubscribe(getRootProvider());
      }
    }

    public void jdkNameChanged(ProjectJdk jdk, String previousName) {
      String currentName = getJdkName();
      if (jdk.getName().equals(currentName)) {
        // if current name matches my name
        updateFromRootProviderAndSubscribe(getRootProvider());
      }
    }

    private boolean isAffectedByJdkRemoval(ProjectJdk jdk) {
      return jdk.getName().equals(getJdkName());
    }
  }

  private class MyProjectJdkListener implements ProjectRootManagerEx.ProjectJdkListener {
    public void projectJdkChanged() {
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }
}
