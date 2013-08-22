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
package com.intellij.tasks;

import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 18.07.13
 */
@Tag("branch")
public class BranchInfo {

  @Attribute("name")
  public String name;

  @Attribute("repository")
  public String repository;

  @Attribute("original")
  public boolean original;

  public static List<BranchInfo> fromTaskInfo(VcsTaskHandler.TaskInfo taskInfo, boolean original) {
    ArrayList<BranchInfo> list = new ArrayList<BranchInfo>();
    for (Map.Entry<String, Collection<String>> entry : taskInfo.branches.entrySet()) {
      for (String repository : entry.getValue()) {
        BranchInfo branchInfo = new BranchInfo();
        branchInfo.name = entry.getKey();
        branchInfo.repository = repository;
        branchInfo.original = original;
        list.add(branchInfo);
      }
    }
    return list;
  }
}
