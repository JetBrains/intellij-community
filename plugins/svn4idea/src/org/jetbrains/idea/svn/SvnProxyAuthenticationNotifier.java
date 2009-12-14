/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.GenericNotifierImpl;
import org.jetbrains.annotations.NotNull;

// todo be done
// todo : two kinds. positive: either into common proxy, or into system properties 
public class SvnProxyAuthenticationNotifier extends GenericNotifierImpl<String, String> {
  public SvnProxyAuthenticationNotifier(Project project, @NotNull String groupId, @NotNull String title, @NotNull NotificationType type) {
    super(project, groupId, title, type);
  }

  @Override
  protected boolean ask(String obj) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  protected String getKey(String obj) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  protected String getNotificationContent(String obj) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
