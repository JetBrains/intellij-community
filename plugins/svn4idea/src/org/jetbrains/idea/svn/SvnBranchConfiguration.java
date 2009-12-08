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

package org.jetbrains.idea.svn;

import org.jetbrains.idea.svn.integrate.SvnBranchItem;

import java.util.*;

/**
 * @author yole
 */
public class SvnBranchConfiguration {
  private String myTrunkUrl;
  private List<String> myBranchUrls;
  private Map<String, List<SvnBranchItem>> myBranchMap;
  private boolean myUserinfoInUrl;

  public SvnBranchConfiguration() {
    myBranchUrls = new ArrayList<String>();
    myBranchMap = new HashMap<String, List<SvnBranchItem>>();
  }

  public boolean isUserinfoInUrl() {
    return myUserinfoInUrl;
  }

  public void setUserinfoInUrl(final boolean userinfoInUrl) {
    myUserinfoInUrl = userinfoInUrl;
  }
  
  public void setBranchUrls(final List<String> branchUrls) {
    myBranchUrls = branchUrls;
    Collections.sort(myBranchUrls);
  }

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  public List<String> getBranchUrls() {
    return myBranchUrls;
  }

  public Map<String, List<SvnBranchItem>> getBranchMap() {
    return myBranchMap;
  }

  public void setBranchMap(final Map<String, List<SvnBranchItem>> branchMap) {
    myBranchMap = branchMap;
  }
}
