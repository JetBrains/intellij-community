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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.idea.svn.api.Depth;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnConfigurationState {

  @Property(surroundWithTag = false)
  public ConfigurationDirectory directory = new ConfigurationDirectory();

  @Tag(value = "keepLocks", textIfEmpty = "true")
  public boolean keepLocks;

  @Tag("myIsUseDefaultProxy")
  public boolean useDefaultProxy;

  // TODO: This seems to be related to old svn versions and should be removed.
  @Tag("supportedVersion")
  public Long supportedVersion;

  @Attribute("maxAnnotateRevisions")
  public int maxAnnotateRevisions = SvnConfiguration.ourMaxAnnotateRevisionsDefault;

  @Attribute("myUseAcceleration")
  public SvnConfiguration.UseAcceleration accelerationType = SvnConfiguration.UseAcceleration.commandLine;

  public boolean runUnderTerminal;

  @Attribute("myAutoUpdateAfterCommit")
  public boolean autoUpdateAfterCommit;

  @Attribute("cleanupOnStartRun")
  public boolean cleanupOnStartRun;

  @Attribute("TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE")
  public Boolean keepNewFilesAsIsForTreeConflictMerge;

  @Attribute("SSL_PROTOCOLS")
  public SvnConfiguration.SSLProtocols sslProtocols =
    SystemInfo.isJavaVersionAtLeast("1.7") ? SvnConfiguration.SSLProtocols.all : SvnConfiguration.SSLProtocols.sslv3;

  @OptionTag("mySSHConnectionTimeout")
  public long sshConnectionTimeout = 30 * 1000;

  @OptionTag("mySSHReadTimeout")
  public long sshReadTimeout = 30 * 1000;

  public SvnConfiguration.SshConnectionType sshConnectionType = SvnConfiguration.SshConnectionType.SUBVERSION_CONFIG;
  public String sshExecutablePath = "";
  public String sshUserName = "";
  public int sshPort = 22;
  public String sshPrivateKeyPath = "";

  public boolean MERGE_DRY_RUN;
  public boolean MERGE_DIFF_USE_ANCESTRY = true;
  public boolean UPDATE_LOCK_ON_DEMAND;
  public boolean IGNORE_SPACES_IN_MERGE;
  public boolean CHECK_NESTED_FOR_QUICK_MERGE;
  public boolean IGNORE_SPACES_IN_ANNOTATE = !PlatformUtils.isPyCharm();
  public boolean SHOW_MERGE_SOURCES_IN_ANNOTATE = true;
  public boolean FORCE_UPDATE;
  public boolean IGNORE_EXTERNALS;
  public Depth UPDATE_DEPTH = Depth.UNKNOWN;

  @Tag("configuration")
  public static class ConfigurationDirectory {

    @Text
    public String path = "";

    @Attribute("useDefault")
    public boolean useDefault = true;
  }
}
