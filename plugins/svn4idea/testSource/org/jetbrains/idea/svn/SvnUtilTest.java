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
package org.jetbrains.idea.svn;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/9/12
 * Time: 5:46 PM
 */
public class SvnUtilTest extends TestCase {
  public static final String URL1 = "http://one/two parts/three";
  public static final String URL2 = "http://one/two/parts/three";

  public void testUrlAppend() throws Exception {
    final SVNURL base = SVNURL.parseURIDecoded(URL1);
    final String subPath = "/one more space/and more";
    final SVNURL url1 = SvnUtil.appendMultiParts(base, subPath);
    Assert.assertEquals(SVNURL.parseURIDecoded(URL1 + subPath), url1);

    final SVNURL base1 = SVNURL.parseURIDecoded(URL2);
    final String subPath1 = "/one\\more\\space/and/more";
    final SVNURL url2 = SvnUtil.appendMultiParts(base1, subPath1);
    Assert.assertEquals(SVNURL.parseURIDecoded(URL2 + subPath1.replace('\\', '/')), url2);

    final String result = SVNPathUtil.append("http://one", "test/multi/parts");
  }
}
