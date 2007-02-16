/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SvnBranchConfiguration {
  private String myTrunkUrl;
  private List<String> myBranchUrls = new ArrayList<String>();

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public void setBranchUrls(final List<String> branchUrls) {
    myBranchUrls = branchUrls;
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  public List<String> getBranchUrls() {
    return myBranchUrls;
  }

  public SvnBranchConfiguration clone() {
    SvnBranchConfiguration result = new SvnBranchConfiguration();
    result.myTrunkUrl = myTrunkUrl;
    result.myBranchUrls = new ArrayList<String>(myBranchUrls);
    return result;
  }

  @Nullable
  public String getBaseUrl(String url) {
    if (url.startsWith(myTrunkUrl)) {
      return myTrunkUrl;
    }
    for(String branchUrl: myBranchUrls) {
      if (url.startsWith(branchUrl)) {
        int pos = url.indexOf('/', branchUrl.length()+1);
        if (pos >= 0) {
          return url.substring(0, pos);
        }
        return branchUrl;
      }
    }
    return null;
  }

  @Nullable
  public String getRelativeUrl(String url) {
    String baseUrl = getBaseUrl(url);
    return baseUrl == null ? null : url.substring(baseUrl.length());
  }
}
