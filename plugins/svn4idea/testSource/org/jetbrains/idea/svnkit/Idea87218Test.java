/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svnkit;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/7/12
 * Time: 11:47 AM
 */
public class Idea87218Test {
  private final static String ourWcPath = "C:\\TestProjects\\sortedProjects\\Subversion\\Sasha\\moveTest";

  public static void main(String[] args) {
    try {
      SVNStatusClient client = new SVNStatusClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      File src = new File(ourWcPath, "file1.txt");
      SVNStatus status = client.doStatus(src, false);
      assert status != null && SVNStatusType.STATUS_NORMAL.equals(status.getNodeStatus());

      File dir = new File(ourWcPath, "unversioned");
      SVNStatus dirStatus = client.doStatus(dir, false);
      assert dirStatus != null && SVNStatusType.STATUS_UNVERSIONED.equals(dirStatus.getNodeStatus());

      File dst = new File(dir, "file1.txt");

      /*
      final SVNCopyClient copyClient = new SVNCopyClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      final SVNCopySource svnCopySource = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.WORKING, src);
      copyClient.doCopy(new SVNCopySource[]{svnCopySource}, dst, true, false, true);
      */
      SVNMoveClient moveClient = new SVNMoveClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      moveClient.doMove(src, dst);
    }
    catch (SVNException e) {
      e.printStackTrace();
    }
  }
}
