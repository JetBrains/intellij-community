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

package org.jetbrains.idea.svn.branchConfig;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default constructor and setters are necessary for serialization purposes.
 *
 * @author yole
 */
@SuppressWarnings("UnusedDeclaration")
public class SvnBranchConfiguration {
  private String myTrunkUrl;
  @NotNull private List<String> myBranchUrls;
  private boolean myUserinfoInUrl;

  public SvnBranchConfiguration() {
    myBranchUrls = new ArrayList<>();
  }

  public SvnBranchConfiguration(String trunkUrl, @NotNull List<String> branchUrls, boolean userinfoInUrl) {
    myTrunkUrl = trunkUrl;
    myBranchUrls = branchUrls;
    Collections.sort(myBranchUrls);
    myUserinfoInUrl = userinfoInUrl;
  }

  public boolean isUserinfoInUrl() {
    return myUserinfoInUrl;
  }

  public void setUserinfoInUrl(final boolean userinfoInUrl) {
    myUserinfoInUrl = userinfoInUrl;
  }

  public void setBranchUrls(@NotNull List<String> branchUrls) {
    myBranchUrls = branchUrls;
    Collections.sort(myBranchUrls);
  }

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  @NotNull
  public List<String> getBranchUrls() {
    return myBranchUrls;
  }
}
