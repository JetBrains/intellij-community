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

import org.apache.subversion.javahl.types.Depth;
import org.tmatesoft.svn.core.SVNDepth;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 7:09 PM
 */
public class DepthConvertor {
  public static SVNDepth convert(Depth depth) {
    if (Depth.infinity.equals(depth)) {
      return SVNDepth.INFINITY;
    } else if (Depth.unknown.equals(depth)) {
      return SVNDepth.UNKNOWN;
    } else if (Depth.empty.equals(depth)) {
      return SVNDepth.EMPTY;
    } else if (Depth.exclude.equals(depth)) {
      return SVNDepth.EXCLUDE;
    } else if (Depth.files.equals(depth)) {
      return SVNDepth.FILES;
    } else if (Depth.immediates.equals(depth)) {
      return SVNDepth.IMMEDIATES;
    }
    throw new UnsupportedOperationException();
  }

  public static Depth convert(SVNDepth depth) {
    if (SVNDepth.INFINITY.equals(depth)) {
      return Depth.infinity;
    } else if (SVNDepth.UNKNOWN.equals(depth)) {
      return Depth.unknown;
    } else if (SVNDepth.EMPTY.equals(depth)) {
      return Depth.empty;
    } else if (SVNDepth.EXCLUDE.equals(depth)) {
      return Depth.exclude;
    } else if (SVNDepth.FILES.equals(depth)) {
      return Depth.files;
    } else if (SVNDepth.IMMEDIATES.equals(depth)) {
      return Depth.immediates;
    }
    throw new UnsupportedOperationException();
  }
}
