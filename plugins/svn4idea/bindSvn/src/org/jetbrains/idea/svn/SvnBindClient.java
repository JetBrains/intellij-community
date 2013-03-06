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

import org.tigris.subversion.javahl.*;

import java.io.OutputStream;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/5/13
 * Time: 3:08 PM
 */
public class SvnBindClient implements SVNClientInterface {
  private final String myExecutablePath;
  private CommitEventHandler myHandler;
  private AuthenticationCallback myAuthenticationCallback;

  public SvnBindClient(String path) {
    myExecutablePath = path;
  }

  @Override
  public void dispose() {
  }

  @Override
  public Version getVersion() {
    // todo real version
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAdminDirectoryName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAdminDirectory(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getLastPath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status singleStatus(String path, boolean onServer) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status[] status(String path, boolean descend, boolean onServer, boolean getAll) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status[] status(String path, boolean descend, boolean onServer, boolean getAll, boolean noIgnore) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Status[] status(String path, boolean descend, boolean onServer, boolean getAll, boolean noIgnore, boolean ignoreExternals)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void status(String path,
                     int depth,
                     boolean onServer,
                     boolean getAll,
                     boolean noIgnore,
                     boolean ignoreExternals,
                     String[] changelists,
                     StatusCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirEntry[] list(String url, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void list(String url,
                   Revision revision,
                   Revision pegRevision,
                   int depth,
                   int direntFields,
                   boolean fetchLocks,
                   ListCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void username(String username) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void password(String password) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPrompt(PromptUserPassword prompt) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public LogMessage[] logMessages(String path,
                                  Revision revisionStart,
                                  Revision revisionEnd,
                                  boolean stopOnCopy,
                                  boolean discoverPath,
                                  long limit) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void logMessages(String path,
                          Revision pegRevision,
                          Revision revisionStart,
                          Revision revisionEnd,
                          boolean stopOnCopy,
                          boolean discoverPath,
                          boolean includeMergedRevisions,
                          String[] revProps,
                          long limit,
                          LogMessageCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void logMessages(String path,
                          Revision pegRevision,
                          RevisionRange[] ranges,
                          boolean stopOnCopy,
                          boolean discoverPath,
                          boolean includeMergedRevisions,
                          String[] revProps,
                          long limit,
                          LogMessageCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long checkout(String moduleName, String destPath, Revision revision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long checkout(String moduleName,
                       String destPath,
                       Revision revision,
                       Revision pegRevision,
                       boolean recurse,
                       boolean ignoreExternals) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long checkout(String moduleName,
                       String destPath,
                       Revision revision,
                       Revision pegRevision,
                       int depth,
                       boolean ignoreExternals,
                       boolean allowUnverObstructions) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void notification(Notify notify) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void notification2(Notify2 notify) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setConflictResolver(ConflictResolverCallback listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setProgressListener(ProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitMessageHandler(CommitMessage messageHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(String[] path, String message, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(String[] path, String message, boolean force, boolean keepLocal, Map revpropTable) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void revert(String path, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void revert(String path, int depth, String[] changelists) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(String path, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(String path, boolean recurse, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(String path, int depth, boolean force, boolean noIgnores, boolean addParents) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long update(String path, Revision revision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long[] update(String[] path, Revision revision, boolean recurse, boolean ignoreExternals) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long update(String path,
                     Revision revision,
                     int depth,
                     boolean depthIsSticky,
                     boolean ignoreExternals,
                     boolean allowUnverObstructions) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long[] update(String[] path,
                       Revision revision,
                       int depth,
                       boolean depthIsSticky,
                       boolean ignoreExternals,
                       boolean allowUnverObstructions) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long commit(String[] path, String message, boolean recurse) throws ClientException {
    return commit(path, message, recurse? 3 : 0, false, false, null, null);
  }

  @Override
  public long commit(String[] path, String message, boolean recurse, boolean noUnlock) throws ClientException {
    return commit(path, message, recurse? 3 : 0, noUnlock, false, null, null);
  }

  @Override
  public long commit(String[] path,
                     String message,
                     int depth,
                     boolean noUnlock,
                     boolean keepChangelist,
                     String[] changelists,
                     Map revpropTable) throws ClientException {
    final long commit = new SvnCommitRunner(myExecutablePath, myHandler, myAuthenticationCallback).
        commit(path, message, depth, noUnlock, keepChangelist, changelists, revpropTable);
    if (commit < 0) {
      throw new BindClientException("Wrong committed revision number: " + commit, null, -1);
    }
    return commit;
  }

  @Override
  public void copy(CopySource[] sources,
                   String destPath,
                   String message,
                   boolean copyAsChild,
                   boolean makeParents,
                   boolean ignoreExternals,
                   Map revpropTable) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copy(CopySource[] sources, String destPath, String message, boolean copyAsChild, boolean makeParents, Map revpropTable)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copy(String srcPath, String destPath, String message, Revision revision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(String[] srcPaths,
                   String destPath,
                   String message,
                   boolean force,
                   boolean moveAsChild,
                   boolean makeParents,
                   Map revpropTable) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(String srcPath, String destPath, String message, Revision ignored, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(String srcPath, String destPath, String message, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void mkdir(String[] path, String message, boolean makeParents, Map revpropTable) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void mkdir(String[] path, String message) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanup(String path) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resolve(String path, int depth, int conflictResult) throws SubversionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resolved(String path, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long doExport(String srcPath, String destPath, Revision revision, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long doExport(String srcPath,
                       String destPath,
                       Revision revision,
                       Revision pegRevision,
                       boolean force,
                       boolean ignoreExternals,
                       boolean recurse,
                       String nativeEOL) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long doExport(String srcPath,
                       String destPath,
                       Revision revision,
                       Revision pegRevision,
                       boolean force,
                       boolean ignoreExternals,
                       int depth,
                       String nativeEOL) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long doSwitch(String path,
                       String url,
                       Revision revision,
                       Revision pegRevision,
                       int depth,
                       boolean depthIsSticky,
                       boolean ignoreExternals,
                       boolean allowUnverObstructions) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void doImport(String path,
                       String url,
                       String message,
                       int depth,
                       boolean noIgnore,
                       boolean ignoreUnknownNodeTypes,
                       Map revpropTable) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] suggestMergeSources(String path, Revision pegRevision) throws SubversionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void merge(String path1,
                    Revision revision1,
                    String path2,
                    Revision revision2,
                    String localPath,
                    boolean force,
                    boolean recurse,
                    boolean ignoreAncestry,
                    boolean dryRun) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void merge(String path1,
                    Revision revision1,
                    String path2,
                    Revision revision2,
                    String localPath,
                    boolean force,
                    int depth,
                    boolean ignoreAncestry,
                    boolean dryRun,
                    boolean recordOnly) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void merge(String path,
                    Revision pegRevision,
                    Revision revision1,
                    Revision revision2,
                    String localPath,
                    boolean force,
                    boolean recurse,
                    boolean ignoreAncestry,
                    boolean dryRun) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void merge(String path,
                    Revision pegRevision,
                    RevisionRange[] revisions,
                    String localPath,
                    boolean force,
                    int depth,
                    boolean ignoreAncestry,
                    boolean dryRun,
                    boolean recordOnly) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void mergeReintegrate(String path, Revision pegRevision, String localPath, boolean dryRun) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Mergeinfo getMergeinfo(String path, Revision pegRevision) throws SubversionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getMergeinfoLog(int kind,
                              String pathOrUrl,
                              Revision pegRevision,
                              String mergeSourceUrl,
                              Revision srcPegRevision,
                              boolean discoverChangedPaths,
                              int depth,
                              String[] revProps,
                              LogMessageCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getMergeinfoLog(int kind,
                              String pathOrUrl,
                              Revision pegRevision,
                              String mergeSourceUrl,
                              Revision srcPegRevision,
                              boolean discoverChangedPaths,
                              String[] revProps,
                              LogMessageCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target1,
                   Revision revision1,
                   String target2,
                   Revision revision2,
                   String outFileName,
                   boolean recurse,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target1,
                   Revision revision1,
                   String target2,
                   Revision revision2,
                   String relativeToDir,
                   String outFileName,
                   int depth,
                   String[] changelists,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force,
                   boolean copiesAsAdds) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target1,
                   Revision revision1,
                   String target2,
                   Revision revision2,
                   String relativeToDir,
                   String outFileName,
                   int depth,
                   String[] changelists,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target,
                   Revision pegRevision,
                   Revision startRevision,
                   Revision endRevision,
                   String outFileName,
                   boolean recurse,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target,
                   Revision pegRevision,
                   Revision startRevision,
                   Revision endRevision,
                   String relativeToDir,
                   String outFileName,
                   int depth,
                   String[] changelists,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force,
                   boolean copiesAsAdds) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diff(String target,
                   Revision pegRevision,
                   Revision startRevision,
                   Revision endRevision,
                   String relativeToDir,
                   String outFileName,
                   int depth,
                   String[] changelists,
                   boolean ignoreAncestry,
                   boolean noDiffDeleted,
                   boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diffSummarize(String target1,
                            Revision revision1,
                            String target2,
                            Revision revision2,
                            int depth,
                            String[] changelists,
                            boolean ignoreAncestry,
                            DiffSummaryReceiver receiver) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void diffSummarize(String target,
                            Revision pegRevision,
                            Revision startRevision,
                            Revision endRevision,
                            int depth,
                            String[] changelists,
                            boolean ignoreAncestry,
                            DiffSummaryReceiver receiver) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData[] properties(String path) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData[] properties(String path, Revision revision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData[] properties(String path, Revision revision, Revision pegRevision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void properties(String path, Revision revision, Revision pegRevision, int depth, String[] changelists, ProplistCallback callback)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertySet(String path, String name, String value, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertySet(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertySet(String path, String name, byte[] value, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertySet(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertySet(String path, String name, String value, int depth, String[] changelists, boolean force, Map revpropTable)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyRemove(String path, String name, int depth, String[] changelists) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyCreate(String path, String name, String value, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyCreate(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyCreate(String path, String name, byte[] value, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyCreate(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void propertyCreate(String path, String name, String value, int depth, String[] changelists, boolean force)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData revProperty(String path, String name, Revision rev) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData[] revProperties(String path, Revision rev) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRevProperty(String path, String name, Revision rev, String value, String originalValue, boolean force)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData propertyGet(String path, String name) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData propertyGet(String path, String name, Revision revision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PropertyData propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] fileContent(String path, Revision revision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void streamFileContent(String path, Revision revision, Revision pegRevision, int bufferSize, OutputStream stream)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void blame(String path, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, BlameCallback callback)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void blame(String path,
                    Revision pegRevision,
                    Revision revisionStart,
                    Revision revisionEnd,
                    boolean ignoreMimeType,
                    boolean includeMergedRevisions,
                    BlameCallback2 callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void blame(String path,
                    Revision pegRevision,
                    Revision revisionStart,
                    Revision revisionEnd,
                    boolean ignoreMimeType,
                    boolean includeMergedRevisions,
                    BlameCallback3 callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setConfigDirectory(String configDir) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getConfigDirectory() throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cancelOperation() throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Info info(String path) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addToChangelist(String[] paths, String changelist, int depth, String[] changelists) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeFromChangelists(String[] paths, int depth, String[] changelists) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getChangelists(String rootPath, String[] changelists, int depth, ChangelistCallback callback) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(String[] path, String comment, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unlock(String[] path, boolean force) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Info2[] info2(String pathOrUrl, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void info2(String pathOrUrl, Revision revision, Revision pegRevision, int depth, String[] changelists, InfoCallback callback)
    throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void upgrade(String path) throws ClientException {
    throw new UnsupportedOperationException();
  }

  public void setHandler(CommitEventHandler handler) {
    myHandler = handler;
  }

  public void setAuthenticationCallback(AuthenticationCallback authenticationCallback) {
    myAuthenticationCallback = authenticationCallback;
  }
}
