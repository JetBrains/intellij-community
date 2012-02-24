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
package org.jetbrains.idea.svn.portable;

import org.apache.subversion.javahl.types.NodeKind;
import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 7:45 PM
 */
public class NodeKindConvertor {
  public static int convert(SVNNodeKind kind) {
    return JavaHLObjectFactory.getNodeKind(kind);
  }

  public static SVNNodeKind convert(NodeKind kind) {
    if (NodeKind.dir == kind) {
      return SVNNodeKind.DIR;
    } else if (NodeKind.none == kind) {
      return SVNNodeKind.NONE;
    } else if (NodeKind.file == kind) {
      return SVNNodeKind.FILE;
    }
    return SVNNodeKind.UNKNOWN;
  }
}
