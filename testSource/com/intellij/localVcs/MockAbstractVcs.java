package com.intellij.localVcs;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.localVcs.LocalVcsServices;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatusProvider;
import com.intellij.openapi.vcs.UpToDateRevisionProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

public class MockAbstractVcs extends AbstractVcs implements ProjectComponent {
  private boolean myMarkExternalChangesAsCurrent = false;
  private UpToDateRevisionProvider myUpToDateRevisionProvider;
  private CheckinEnvironment myCheckinEnvironment;

  public MockAbstractVcs(Project project){
    super(project);
  }

  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  public String getName() {
    return "mock";
  }

  public String getDisplayName() {
    return "mock";
  }

  public Configurable getConfigurable() {
    return null;
  }

  public void checkinFile(String path, Object parameters) throws VcsException {
  }

  public void addFile(String folderPath, String name, Object parameters) throws VcsException {
  }

  public void removeFile(String path, Object parameters) throws VcsException {
  }

  public void addDirectory(String parentPath, String name, Object parameters) throws VcsException {
  }

  public void removeDirectory(String path, Object parameters) throws VcsException {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "mock";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public FileStatusProvider getFileStatusProvider() {
    return LocalVcsServices.getInstance(myProject).getFileStatusProvider();
  }

  public boolean markExternalChangesAsUpToDate() {
    return myMarkExternalChangesAsCurrent ;
  }

  public void setMarkExternalChangesAsCurrent(boolean value){
    myMarkExternalChangesAsCurrent = value;
  }

  public void setUpToDateRevisionProvider(UpToDateRevisionProvider upToDateRevisionProvider) {
    myUpToDateRevisionProvider = upToDateRevisionProvider;
  }

  public UpToDateRevisionProvider getUpToDateRevisionProvider() {
    return myUpToDateRevisionProvider;
  }

  public void setCheckinEnvironment(CheckinEnvironment ce) {
    myCheckinEnvironment = ce;
  }
}
