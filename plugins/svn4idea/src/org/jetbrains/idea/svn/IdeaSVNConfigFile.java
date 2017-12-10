// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.config.DefaultProxyGroup;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import static com.intellij.openapi.util.io.FileSystemUtil.lastModified;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.containers.ContainerUtil.union;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class IdeaSVNConfigFile {

  public final static String SERVERS_FILE_NAME = "servers";
  public final static String CONFIG_FILE_NAME = "config";

  public static final String DEFAULT_GROUP_NAME = "global";
  public static final String GROUPS_GROUP_NAME = "groups";

  @NotNull private final Map<String, String> myPatternsMap = new HashMap<>();
  private final long myLatestUpdate = -1;
  @NotNull private final Path myPath;
  @NotNull private final Map<String, String> myDefaultProperties = new HashMap<>();
  @NotNull private final SVNConfigFile mySVNConfigFile;

  public IdeaSVNConfigFile(@NotNull Path path) {
    mySVNConfigFile = new SVNConfigFile(path.toFile());
    myPath = path;
  }

  @NotNull
  public static String getNewGroupName(@NotNull String host, @NotNull IdeaSVNConfigFile configFile) {
    String groupName = host;
    final Map<String, ProxyGroup> groups = configFile.getAllGroups();
    while (StringUtil.isEmptyOrSpaces(groupName) || groups.containsKey(groupName)) {
      groupName += "1";
    }
    return groupName;
  }

  public void updateGroups() {
    if (myLatestUpdate != lastModified(myPath.toFile())) {
      myPatternsMap.clear();
      myPatternsMap.putAll(getValues(GROUPS_GROUP_NAME));

      myDefaultProperties.clear();
      myDefaultProperties.putAll(getValues(DEFAULT_GROUP_NAME));
    }
  }

  @NotNull
  public Map<String, ProxyGroup> getAllGroups() {
    final Map<String, ProxyGroup> result = new HashMap<>(myPatternsMap.size());
    for (Map.Entry<String, String> entry : myPatternsMap.entrySet()) {
      final String groupName = entry.getKey();
      result.put(groupName, new ProxyGroup(groupName, entry.getValue(), getValues(groupName)));
    }
    return result;
  }

  @NotNull
  public DefaultProxyGroup getDefaultGroup() {
    return new DefaultProxyGroup(myDefaultProperties);
  }

  @Nullable
  public String getValue(@NotNull String groupName, @NotNull String propertyName) {
    return mySVNConfigFile.getPropertyValue(groupName, propertyName);
  }

  @NotNull
  public Map<String, String> getValues(@NotNull String groupName) {
    return mySVNConfigFile.getProperties(groupName);
  }

  public void setValue(@NotNull String groupName, @NotNull String propertyName, @Nullable String value) {
    mySVNConfigFile.setPropertyValue(groupName, propertyName, value, false);
  }

  public void deleteGroup(@NotNull String name) {
    // remove all properties
    final Map<String, String> properties = getValues(name);
    for (String propertyName : properties.keySet()) {
      setValue(name, propertyName, null);
    }
    if (DEFAULT_GROUP_NAME.equals(name)) {
      myDefaultProperties.clear();
    }
    // remove group from groups
    setValue(GROUPS_GROUP_NAME, name, null);
    mySVNConfigFile.deleteGroup(name, false);
  }

  public void addGroup(@NotNull String name, @Nullable String patterns, @NotNull Map<String, String> properties) {
    setValue(GROUPS_GROUP_NAME, name, patterns);
    addProperties(name, properties);
  }

  private void addProperties(@NotNull String groupName, @NotNull Map<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      setValue(groupName, entry.getKey(), entry.getValue());
    }
  }

  public void modifyGroup(@NotNull String name,
                          @Nullable String patterns,
                          @NotNull Collection<String> delete,
                          @NotNull Map<String, String> addOrModify,
                          boolean isDefault) {
    if (!isDefault) {
      setValue(GROUPS_GROUP_NAME, name, patterns);
    }
    final Map<String, String> deletedPrepared = new HashMap<>(delete.size());
    for (String property : delete) {
      deletedPrepared.put(property, null);
    }
    addProperties(name, deletedPrepared);
    addProperties(name, addOrModify);
  }

  public void save() {
    mySVNConfigFile.save();
  }

  @Nullable
  public static String getPropertyIdea(@NotNull String host, @NotNull Couple<IdeaSVNConfigFile> serversFile, @NotNull String name) {
    String groupName = getGroupName(getValues(serversFile, GROUPS_GROUP_NAME), host);
    if (groupName != null) {
      Map<String, String> hostProps = getValues(serversFile, groupName);
      final String value = hostProps.get(name);
      if (value != null) {
        return value;
      }
    }
    return getValues(serversFile, DEFAULT_GROUP_NAME).get(name);
  }

  public static boolean checkHostGroup(@NotNull String url, @Nullable String patterns, @Nullable String exceptions) {
    final Url svnurl;
    try {
      svnurl = createUrl(url);
    }
    catch (SvnBindException e) {
      return false;
    }

    final String host = svnurl.getHost();
    return matches(patterns, host) && (!matches(exceptions, host));
  }

  private static boolean matches(@Nullable String pattern, @NotNull String host) {
    StringTokenizer tokenizer = new StringTokenizer(notNullize(pattern), ",");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (DefaultSVNOptions.matches(token, host)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getGroupForHost(@NotNull String host, @NotNull IdeaSVNConfigFile serversFile) {
    final Map<String, ProxyGroup> groups = serversFile.getAllGroups();
    for (Map.Entry<String, ProxyGroup> entry : groups.entrySet()) {
      if (matches(entry.getValue().getPatterns(), host)) return entry.getKey();
    }
    return null;
  }

  @Nullable
  private static String getGroupName(@NotNull Map<String, String> groups, @NotNull String host) {
    for (Map.Entry<String, String> entry : groups.entrySet()) {
      if (matches(entry.getValue(), host)) return entry.getKey();
    }
    return null;
  }

  public static boolean isTurned(@Nullable String value) {
    return value == null || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
  }

  @Nullable
  public static String getValue(@NotNull Couple<IdeaSVNConfigFile> files, @NotNull String groupName, @NotNull String propertyName) {
    String result = files.second.getValue(groupName, propertyName);
    return result != null ? result : files.first.getValue(groupName, propertyName);
  }

  @NotNull
  public static Map<String, String> getValues(@NotNull Couple<IdeaSVNConfigFile> files, @NotNull String groupName) {
    return union(files.first.getValues(groupName), files.second.getValues(groupName));
  }
}
