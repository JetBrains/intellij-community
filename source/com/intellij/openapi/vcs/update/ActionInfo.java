package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.Configurable;

import java.util.LinkedHashMap;

public interface ActionInfo {  
  ActionInfo UPDATE = new ActionInfo() {
    public boolean showOptions(Project project) {
      return VcsConfiguration.getInstance(project).SHOW_UPDATE_OPTIONS;
    }

    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getUpdateEnvironment();
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable, UpdateEnvironment> envToConfMap) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName();
        }

        protected boolean isToBeShown() {
          return VcsConfiguration.getInstance(project).SHOW_UPDATE_OPTIONS;
        }

        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            VcsConfiguration.getInstance(project).SHOW_UPDATE_OPTIONS = value;
          }
        }
      };
    }

    public String getActionName() {
      return "Update";
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getUpdateName();
    }
  };

  ActionInfo STATUS = new ActionInfo() {
    public boolean showOptions(Project project) {
      return VcsConfiguration.getInstance(project).SHOW_STATUS_OPTIONS;
    }

    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getStatusEnvironment();
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable, UpdateEnvironment> envToConfMap) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName();
        }

        protected boolean isToBeShown() {
          return VcsConfiguration.getInstance(project).SHOW_STATUS_OPTIONS;
        }

        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            VcsConfiguration.getInstance(project).SHOW_STATUS_OPTIONS = value;
          }
        }
      };
    }

    public String getActionName() {
      return "Check Status for";
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getStatusName();
    }
  };

  boolean showOptions(Project project);

  UpdateEnvironment getEnvironment(AbstractVcs vcs);

  UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, UpdateEnvironment> envToConfMap);

  String getActionName();

  String getGroupName(FileGroup fileGroup);
}
