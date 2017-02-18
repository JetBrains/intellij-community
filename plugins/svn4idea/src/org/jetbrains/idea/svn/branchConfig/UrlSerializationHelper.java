/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
* @author Konstantin Kolosovsky.
*/
public class UrlSerializationHelper {
  private final SvnVcs myVcs;

  public UrlSerializationHelper(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public SvnBranchConfiguration prepareForSerialization(final SvnBranchConfiguration configuration) {
    final Ref<Boolean> withUserInfo = new Ref<>();
    final String trunkUrl = serializeUrl(configuration.getTrunkUrl(), withUserInfo);

    if (Boolean.FALSE.equals(withUserInfo.get())) {
      return configuration;
    }

    final List<String> branches = configuration.getBranchUrls();
    final List<String> newBranchesList = new ArrayList<>(branches.size());
    for (String s : branches) {
      newBranchesList.add(serializeUrl(s, withUserInfo));
    }

    return new SvnBranchConfiguration(trunkUrl, newBranchesList, withUserInfo.isNull() ? false : withUserInfo.get());
  }

  public SvnBranchConfiguration afterDeserialization(final String path, final SvnBranchConfiguration configuration) {
    if (! configuration.isUserinfoInUrl()) {
      return configuration;
    }
    final String userInfo = getUserInfo(path);
    if (userInfo == null) {
      return configuration;
    }

    final String newTrunkUrl = deserializeUrl(configuration.getTrunkUrl(), userInfo);
    final List<String> branches = configuration.getBranchUrls();
    final List<String> newBranchesList = new ArrayList<>(branches.size());
    for (String s : branches) {
      newBranchesList.add(deserializeUrl(s, userInfo));
    }

    return new SvnBranchConfiguration(newTrunkUrl, newBranchesList, userInfo.length() > 0);
  }

  private static String serializeUrl(final String url, final Ref<Boolean> withUserInfo) {
    if (Boolean.FALSE.equals(withUserInfo.get())) {
      return url;
    }
    try {
      final SVNURL svnurl = SVNURL.parseURIEncoded(url);
      if (withUserInfo.isNull()) {
        final String userInfo = svnurl.getUserInfo();
        withUserInfo.set((userInfo != null) && (userInfo.length() > 0));
      }
      if (withUserInfo.get()) {
        return SVNURL.create(svnurl.getProtocol(), null, svnurl.getHost(), SvnUtil.resolvePort(svnurl), svnurl.getURIEncodedPath(), true)
          .toString();
      }
    }
    catch (SVNException e) {
      //
    }
    return url;
  }

  @Nullable
  private String getUserInfo(final String path) {
    final SVNURL svnurl = myVcs.getSvnFileUrlMapping().getUrlForFile(new File(path));
    return svnurl != null ? svnurl.getUserInfo() : null;
  }

  private static String deserializeUrl(final String url, final String userInfo) {
    try {
      final SVNURL svnurl = SVNURL.parseURIEncoded(url);
      return SVNURL.create(svnurl.getProtocol(), userInfo, svnurl.getHost(), SvnUtil.resolvePort(svnurl), svnurl.getURIEncodedPath(),
                           true).toString();
    } catch (SVNException e) {
      return url;
    }
  }
}
