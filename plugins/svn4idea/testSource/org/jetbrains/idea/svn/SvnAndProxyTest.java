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
package org.jetbrains.idea.svn;

import com.intellij.util.net.HttpConfigurable;

public class SvnAndProxyTest extends Svn17TestCase {
  @Test
  public void testDirectWhenIdeaHaveProxy() {


  }

  private static void setDefaultFixedProxySettings() {
    final HttpConfigurable h = HttpConfigurable.getInstance();
    h.USE_PROXY_PAC = false;
    h.USE_HTTP_PROXY = true;
    h.AUTHENTICATION_CANCELLED = false;
    // doesn't matter, only significant for serialization
    h.KEEP_PROXY_PASSWORD = false;
    h.LAST_ERROR = null;
  }

  private @interface Test{}
}
