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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.impl.GenericNotifier;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Set;

public class SvnAuthenticationNotifier extends GenericNotifier<SvnAuthenticationNotifier.AuthenticationRequest, Pair<String, SVNURL>> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnAuthenticationNotifier");
  private final static ReentranceDefence ourDefence = new ReentranceDefence();

  private static final String ourGroupId = "Subversion";
  private final SvnVcs myVcs;

  public SvnAuthenticationNotifier(final SvnVcs svnVcs) {
    super(svnVcs.getProject(), ourGroupId, "Not Logged To Subversion", NotificationType.ERROR);
    myVcs = svnVcs;
  }

  @Override
  protected boolean ask(final AuthenticationRequest obj) {
    triggerAsk(getKey(obj));
    return obj.validate();
  }

  @NotNull
  @Override
  protected Pair<String, SVNURL> getKey(final AuthenticationRequest obj) {
    final Set<Pair<String,SVNURL>> keys = canceledKeySet();
    for (Pair<String, SVNURL> key : keys) {
      if (! key.getFirst().equals(obj.getKind())) continue;
      final SVNURL commonURLAncestor = SVNURLUtil.getCommonURLAncestor(key.getSecond(), obj.getUrl());
      if (key.getSecond().equals(commonURLAncestor)) {
        return key;
      }
    }
    return new Pair<String, SVNURL>(obj.getKind(), obj.getUrl());
  }

  @NotNull
  @Override
  protected String getNotificationContent(AuthenticationRequest obj) {
    return "Not logged to Subversion '" + obj.getRealm() + "' (" + obj.getUrl().toDecodedString() + ") + <a href=\"\">Click to fix.</a>";
  }

  public static class AuthenticationRequest {
    private final Project myProject;
    private final String myKind;
    private final SVNURL myUrl;
    private final String myRealm;

    public AuthenticationRequest(Project project, String kind, SVNURL url, String realm) {
      myProject = project;
      myKind = kind;
      myUrl = url;
      myRealm = realm;
    }

    public String getKind() {
      return myKind;
    }

    public SVNURL getUrl() {
      return myUrl;
    }

    public String getRealm() {
      return myRealm;
    }

    public boolean validate() {
      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      try {
        vcs.createWCClient().doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
      } catch (SVNAuthenticationException e) {
        LOG.debug(e);
        return false;
      } catch (SVNException e) {
        LOG.info(e);
        return false;
      }
      return true;
    }
  }
}
