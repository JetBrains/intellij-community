package com.intellij.openapi.vcs.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;

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
      return VcsBundle.message("action.name.update");
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName() + " " + scopeName;
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
      return VcsBundle.message("action.name.update.scope", scopeName);
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
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap, final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return getActionName() + " " + scopeName;
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
      return VcsBundle.message("action.name.check.status");
    }

    public String getActionName(String scopeName) {
      return VcsBundle.message("action.name.check.scope.status", scopeName);
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getStatusName();
    }
  };

  ActionInfo INTEGRATE = new ActionInfo() {
    public boolean showOptions(Project project) {
      return true;
    }

    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getIntegrateEnvironment();
    }

    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        protected String getRealTitle() {
          return "Integrate " + scopeName;
        }

        protected boolean canBeHidden() {
          return false;
        }

        protected boolean isToBeShown() {
          return true;
        }

        protected void setToBeShown(boolean value, boolean onOk) {
        }
      };
    }

    public String getActionName(String scopeName) {
      return VcsBundle.message("action.name.integrate.scope", scopeName);
    }

    public String getActionName() {
      return VcsBundle.message("action.name.integrate");
    }

    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getUpdateName();
    }
  };


  boolean showOptions(Project project);

  UpdateEnvironment getEnvironment(AbstractVcs vcs);

  UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                  final String scopeName);

  String getActionName(String scopeName);

  String getActionName();

  String getGroupName(FileGroup fileGroup);
}
