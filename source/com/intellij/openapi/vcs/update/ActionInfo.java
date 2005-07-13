package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.Configurable;

import java.util.LinkedHashMap;

public interface ActionInfo {  
  ActionInfo UPDATE = new ActionInfo() {
    public boolean showOptions(Project project) {
      return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).getValue();
    }

    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getUpdateEnvironment();
    }

    public String getActionName() {
      return "Update";
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable,AbstractVcs> envToConfMap) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName();
        }

        protected boolean isToBeShown() {
          return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).getValue();
        }

        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(value);
          }
        }
      };
    }

    public String getActionName(String scopeName) {
      return "Update " + scopeName;
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getUpdateName();
    }
  };

  ActionInfo STATUS = new ActionInfo() {
    public boolean showOptions(Project project) {
      return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).getValue();
    }

    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getStatusEnvironment();
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable,AbstractVcs> envToConfMap) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName();
        }

        protected boolean isToBeShown() {
          return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).getValue();
        }

        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).setValue(value);
          }
        }
      };
    }

    public String getActionName() {
      return "Check Status";
    }

    public String getActionName(String scopeName) {
      return "Check " + scopeName + " Status";
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getStatusName();
    }
  };

  boolean showOptions(Project project);

  UpdateEnvironment getEnvironment(AbstractVcs vcs);

  UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable,AbstractVcs> envToConfMap);

  String getActionName(String scopeName);

  String getActionName();

  String getGroupName(FileGroup fileGroup);
}
