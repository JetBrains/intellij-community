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
package com.intellij.ui.errorView.impl;

import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;

public class ErrorViewFactoryImpl implements ErrorViewFactory {
  public ErrorTreeView createErrorTreeView(Project project,
                                           String helpId,
                                           boolean createExitAction,
                                           final AnAction[] extraPopupMenuActions,
                                           final AnAction[] extraRightToolbarGroupActions,
                                           final ContentManagerProvider contentManagerProvider) {
    return new NewErrorTreeViewPanel(project, helpId, createExitAction) {
      protected void addExtraPopupMenuActions(DefaultActionGroup group) {
        super.addExtraPopupMenuActions(group);
        if (extraPopupMenuActions != null){
          for (AnAction extraPopupMenuAction : extraPopupMenuActions) {
            group.add(extraPopupMenuAction);
          }
        }
      }

      protected void fillRightToolbarGroup(DefaultActionGroup group) {
        super.fillRightToolbarGroup(group);
        if (extraRightToolbarGroupActions != null){
          for (AnAction extraRightToolbarGroupAction : extraRightToolbarGroupActions) {
            group.add(extraRightToolbarGroupAction);
          }
        }
      }

      public void close() {
        ContentManager contentManager = contentManagerProvider.getParentContent();
        Content content = contentManager.getContent(this);
        if (content != null) {
          contentManager.removeContent(content);
        }
      }
    };
  }
}
