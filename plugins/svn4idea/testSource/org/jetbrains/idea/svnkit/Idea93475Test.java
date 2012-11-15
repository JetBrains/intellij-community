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
 * Date: 11/15/12
 * Time: 3:39 PM
 */
public class Idea93475Test {
  private final static String ourWcPath = "C:\\TestProjects\\sortedProjects\\Subversion\\Sasha\\renameNewWithVersioned";

  public static void main(String[] args) {
    try {
      SVNStatusClient client = new SVNStatusClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      File src = new File(ourWcPath, "cde\\text1.txt");
      assertStatus(client, src, SVNStatusType.STATUS_NORMAL);

      File src1 = new File(ourWcPath, "cde\\text2.txt");
      assertStatus(client, src, SVNStatusType.STATUS_NORMAL);

      File dir = new File(ourWcPath, "abc");
      assertStatus(client, dir, SVNStatusType.STATUS_ADDED);

      File dst = new File(dir, "text1.txt");
      File dst1 = new File(dir, "text2.txt");

      SVNMoveClient moveClient = new SVNMoveClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      moveClient.doMove(src, dst);
      moveClient.doMove(src1, dst1);

      assertStatus(client, dst, SVNStatusType.STATUS_ADDED);
      assertStatus(client, dst1, SVNStatusType.STATUS_ADDED);

      assert client.doStatus(dst, false).getCopyFromURL() != null;
      assert client.doStatus(dst1, false).getCopyFromURL() != null;

      // now rename the directory
      final File renamedDir = new File(ourWcPath, "abc_renamed");
      moveClient.doMove(dir, renamedDir);

      File dstInRenamed = new File(renamedDir, "text1.txt");
      File dst1InRenamed = new File(renamedDir, "text2.txt");

      assertStatus(client, dstInRenamed, SVNStatusType.STATUS_ADDED);
      assertStatus(client, dst1InRenamed, SVNStatusType.STATUS_ADDED);

      assert client.doStatus(dstInRenamed, false).getCopyFromURL() != null;
      assert client.doStatus(dst1InRenamed, false).getCopyFromURL() != null;
    }
    catch (SVNException e) {
      e.printStackTrace();
    }
  }

  private static void assertStatus(SVNStatusClient client, File src, final SVNStatusType type) throws SVNException {
    SVNStatus status1 = client.doStatus(src, false);
    assert status1 != null && type.equals(status1.getNodeStatus());
  }
}
