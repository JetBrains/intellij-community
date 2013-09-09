/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.tmatesoft.svn.core.SVNURL;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/5/13
 * Time: 3:08 PM
 */
public class SvnBindClient {
  private final String myExecutablePath;
  private CommitEventHandler myHandler;
  private AuthenticationCallback myAuthenticationCallback;
  private Convertor<String[], SVNURL> myUrlProvider;

  public SvnBindClient(String path, Convertor<String[], SVNURL> urlProvider) {
    myExecutablePath = path;
    myUrlProvider = urlProvider;
  }

  public long commit(String[] path, String message, boolean recurse, boolean noUnlock) throws VcsException {
    return commit(path, message, recurse? 3 : 0, noUnlock, false, null, null);
  }

  public long commit(String[] path,
                     String message,
                     int depth,
                     boolean noUnlock,
                     boolean keepChangelist,
                     String[] changelists,
                     Map revpropTable) throws VcsException {
    final long commit = new SvnCommitRunner(myExecutablePath, myHandler, myAuthenticationCallback).
        commit(path, message, depth, noUnlock, keepChangelist, changelists, revpropTable, myUrlProvider);
    if (commit < 0) {
      throw new VcsException("Wrong committed revision number: " + commit);
    }
    return commit;
  }

  public void setHandler(CommitEventHandler handler) {
    myHandler = handler;
  }

  public void setAuthenticationCallback(AuthenticationCallback authenticationCallback) {
    myAuthenticationCallback = authenticationCallback;
  }
}
