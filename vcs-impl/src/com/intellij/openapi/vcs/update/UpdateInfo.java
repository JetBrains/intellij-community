package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UpdateInfo implements JDOMExternalizable {
  private final Project myProject;
  private UpdatedFiles myUpdatedFiles;
  private String myDate;
  private ActionInfo myActionInfo;
  private static final DateFormat DATE_FORMAT =
    SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT, Locale.getDefault());
  @NonNls private static final String DATE_ATTR = "date";
  @NonNls private static final String FILE_INFO_ELEMENTS = "UpdatedFiles";
  @NonNls private static final String ACTION_INFO_ATTRIBUTE_NAME = "ActionInfo";

  public UpdateInfo(Project project, UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myProject = project;
    myActionInfo = actionInfo;
    myUpdatedFiles = updatedFiles;
    myDate = DATE_FORMAT.format(new Date());
  }

  public UpdateInfo(Project project) {
    myProject = project;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myUpdatedFiles == null) return;
    element.setAttribute(DATE_ATTR, myDate);
    element.setAttribute(ACTION_INFO_ATTRIBUTE_NAME, myActionInfo.getActionName());
    Element filesElement = new Element(FILE_INFO_ELEMENTS);
    myUpdatedFiles.writeExternal(filesElement);
    element.addContent(filesElement);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myDate = element.getAttributeValue(DATE_ATTR);
    Element fileInfoElement = element.getChild(FILE_INFO_ELEMENTS);
    if (fileInfoElement == null) return;

    String actionInfoName = element.getAttributeValue(ACTION_INFO_ATTRIBUTE_NAME);

    myActionInfo = getActionInfoByName(actionInfoName);
    if (myActionInfo == null) return;

    UpdatedFiles updatedFiles = UpdatedFiles.create();
    updatedFiles.readExternal(fileInfoElement);
    myUpdatedFiles = updatedFiles;

  }

  private ActionInfo getActionInfoByName(String actionInfoName) {
    if (ActionInfo.UPDATE.getActionName().equals(actionInfoName)) return ActionInfo.UPDATE;
    if (ActionInfo.STATUS.getActionName().equals(actionInfoName)) return ActionInfo.STATUS;
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public Project getPoject() {
    return myProject;
  }

  public UpdatedFiles getFileInformation() {
    return myUpdatedFiles;
  }

  public String getCaption() {
    return VcsBundle.message("toolwindow.title.update.project", myDate);
  }

  public boolean isEmpty() {
    if (myUpdatedFiles != null) {
      return myUpdatedFiles.isEmpty();
    } else {
      return true;
    }    
  }

  public ActionInfo getActionInfo() {
    return myActionInfo;
  }
}