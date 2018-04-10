// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

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
      final Url svnurl = createUrl(url);
      if (withUserInfo.isNull()) {
        final String userInfo = svnurl.getUserInfo();
        withUserInfo.set((userInfo != null) && (userInfo.length() > 0));
      }
      if (withUserInfo.get()) {
        return svnurl.setUserInfo(null).toString();
      }
    }
    catch (SvnBindException ignored) {
    }
    return url;
  }

  @Nullable
  private String getUserInfo(final String path) {
    final Url svnurl = myVcs.getSvnFileUrlMapping().getUrlForFile(new File(path));
    return svnurl != null ? svnurl.getUserInfo() : null;
  }

  private static String deserializeUrl(final String url, final String userInfo) {
    try {
      final Url svnurl = createUrl(url);
      return svnurl.setUserInfo(userInfo).toString();
    }
    catch (SvnBindException e) {
      return url;
    }
  }
}
