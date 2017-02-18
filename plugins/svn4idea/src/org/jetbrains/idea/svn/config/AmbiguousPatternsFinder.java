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
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.util.Ref;
import org.jetbrains.idea.svn.SvnBundle;

import java.util.*;

public class AmbiguousPatternsFinder {
  private final Map<String, Set<String>> urls2groups;

  private final static int ourMessageLen = 30;

  public AmbiguousPatternsFinder() {
    urls2groups = new HashMap<>();
  }

  public void acceptUrls(final String groupName, final List<String> urls) {
    for (String url : urls) {
      final Set<String> set;
      if (urls2groups.containsKey(url)) {
        set = urls2groups.get(url);
      } else {
        set = new HashSet<>();
        urls2groups.put(url, set);
      }
      set.add(groupName);
    }
  }

  public boolean isValid(final Ref<String> errorMessageRef) {
    StringBuilder sb = null;
    for (Map.Entry<String, Set<String>> entry : urls2groups.entrySet()) {
      if (entry.getValue().size() > 1) {
        if (sb == null) {
          sb = new StringBuilder();
        } else {
          if (sb.length() > ourMessageLen) {
            sb.append("...");
            break;
          }
          sb.append("; ");
        }
        StringBuilder innerBuilder = null;
        for (String groupName : entry.getValue()) {
          if (innerBuilder == null) {
            innerBuilder = new StringBuilder();
          } else {
            innerBuilder.append(", ");
          }
          innerBuilder.append(groupName);
        }
        sb.append(SvnBundle.message("dialog.edit.http.proxies.settings.error.ambiguous.group.patterns.to.text",
                                    entry.getKey(), innerBuilder.toString()));
      }
    }
    if (sb != null) {
      errorMessageRef.set(SvnBundle.message("dialog.edit.http.proxies.settings.error.ambiguous.group.patterns.text", sb.toString()));
    }
    return sb == null;
  }
}
