/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

public class CommonCheckinFilesAction extends AbstractCommonCheckinAction {
  protected String getActionName(VcsContext dataContext) {
    FilePath[] roots = getRoots(dataContext);
    if (roots == null || roots.length == 0) return getCheckinActionName(dataContext);
    FilePath first = roots[0];
    if (roots.length == 1) {
      if (first.isDirectory()) {
        return getCheckinActionName(dataContext) + " Directory";
      }
      else {
        return getCheckinActionName(dataContext) + " File";
      }
    }
    else {
      if (first.isDirectory()) {
        return getCheckinActionName(dataContext) + " Directories";
      }
      else {
        return getCheckinActionName(dataContext) + " Files";
      }
    }
  }

  private String getCheckinActionName(VcsContext dataContext) {
    Project project = dataContext.getProject();
    if (project == null) return "Checkin";
    CheckinEnvironment env = getCommonEnvironmentFor(getRoots(dataContext), project);
    if (env == null) {
      return "Checkin";
    }
    else {
      return env.getCheckinOperationName();
    }
  }

  protected FilePath[] getRoots(VcsContext context) {
    return context.getSelectedFilePaths();
  }

  protected boolean shouldShowDialog(VcsContext context) {
    Project project = context.getProject();
    FilePath[] roots = filterDescindingFiles(getRoots(context), project);
    int ciType = getCheckinType(roots);
    if (ciType == DIRECTORIES) {
      return true;
    }
    else {
      return VcsConfiguration.getInstance(project).SHOW_CHECKIN_OPTIONS;
    }
  }
}
