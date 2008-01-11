package com.intellij.util.indexing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */

@State(
  name = "FileBasedIndex",
  storages = {
  @Storage(
    id = "index",
    file = "$APP_CONFIG$/index.xml")
    }
)
public class FileBasedIndex implements ApplicationComponent, PersistentStateComponent<FileBasedIndexState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndex");
  
  private final Map<String, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<String, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final CompositeInputFiler myCompositeFilter = new CompositeInputFiler();  
  private List<Disposable> myDisposables = new ArrayList<Disposable>();
  private FileBasedIndexState myPreviouslyRegistered;
  private Set<String> myProcessedProjects = new HashSet<String>(); // stores locationHash of all projects opened projets during this session
  private List<Runnable> myFlushStorages = new ArrayList<Runnable>();
  
  public static interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }
  
  public static final class FileContent {
    final VirtualFile file;
    final CharSequence content;

    public FileContent(final VirtualFile file, final CharSequence content) {
      this.file = file;
      this.content = content;
    }
  }

  public FileBasedIndex(final VirtualFileManager vfManager, final ProjectManager projectManager) throws IOException {
    final Set<String> requiresRebuild = new HashSet<String>();
    final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
    for (FileBasedIndexExtension extension : extensions) {
      final boolean _requiresRebuild = registerIndexer(
        extension.getName(), 
        extension.getIndexer(), 
        extension.getKeyDescriptor(), 
        extension.getValueExternalizer(), 
        extension.getInputFilter()
      );
      if (_requiresRebuild) {
        requiresRebuild.add(extension.getName());
      }
    }
    
    dropUnregisteredIndices();

    final VirtualFileListener vfsListener = new MyVFSListener();
    vfManager.addVirtualFileListener(vfsListener);
    myDisposables.add(new Disposable() {
      public void dispose() {
        vfManager.removeVirtualFileListener(vfsListener);
      }
    });

    final ProjectManagerListener pmListener = new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
          public void run() {
            scanContent(project, requiresRebuild);
          }
        });
      }
    };
    projectManager.addProjectManagerListener(pmListener);
    myDisposables.add(new Disposable() {
      public void dispose() {
        projectManager.removeProjectManagerListener(pmListener);
      }
    });
  }

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
  }

  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   */
  private <K, V> boolean registerIndexer(final String name, 
                                     final DataIndexer<K, V, FileContent> indexer, 
                                     final PersistentEnumerator.DataDescriptor<K> keyDescriptor, 
                                     final DataExternalizer<V> valueExternalizer, 
                                     final InputFilter filter) throws IOException {
    final File storageFile = getStorageFile(name);
    
    MapIndexStorage<K, V> _storage = null;
    boolean requiresRebuild = false;

    int initAttempt = 0;
    final int maxAttempts = 2;
    while (initAttempt < maxAttempts) {
      initAttempt++;
      requiresRebuild = !storageFile.exists();
      try {
        _storage = new MapIndexStorage<K, V>(storageFile, keyDescriptor, valueExternalizer);
        break;
      }
      catch (IOException e) {
        if (initAttempt < maxAttempts) {
          FileUtil.delete(storageFile);
        }
        else {
          throw e;
        }
      }
    }

    final MapIndexStorage<K, V> storage = _storage;
    myDisposables.add(new Disposable() {
      public void dispose() {
        try {
          storage.close();
        }
        catch (StorageException e) {
          LOG.error(e);
        }
      }
    });
    
    final MapReduceIndex<K, V, FileContent> index = new MapReduceIndex<K, V, FileContent>(indexer, storage);
    myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, filter));
    myCompositeFilter.addFilter(filter);
    myFlushStorages.add(new Runnable() {
      public void run() {
        storage.flush();
      }
    });
    return requiresRebuild;
  }
  
  private void flushStorages() {
    for (Runnable flushStorage : myFlushStorages) {
      flushStorage.run();
    }
  }
  
  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileBasedIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
    myFlushStorages.clear();
  }

  public FileBasedIndexState getState() {
    return new FileBasedIndexState(myIndices.keySet());
  }

  public void loadState(final FileBasedIndexState state) {
    myPreviouslyRegistered = state;
  }

  @NotNull
  public <K, V> List<V> getData(final String indexId, K dataKey, Project project) throws StorageException {
    final AbstractIndex<K, V> index = getIndex(indexId);
    if (index == null) {
      return Collections.emptyList();
    }
    final ValueContainer<V> data = index.getData(dataKey);
    return project == null? data.toValueList() : new ProjectContentFilter<V>(project).apply(data);
  }

  // called for initial content scan on opening a project
  private void scanContent(final Project project, final Set<String> requiresRebuild) {
    final Set<String> indicesToUpdate = new HashSet<String>(myIndices.keySet());
    if (indicesToUpdate.size() == 0) {
      return;
    }

    try {
      final String projectLocationHash = project.getLocationHash();
      final boolean projectAlreadyProcessed = myProcessedProjects.contains(projectLocationHash);
      myProcessedProjects.add(projectLocationHash);

      ProjectRootManager.getInstance(project).getFileIndex().iterateContent(new ContentIterator() {
        public boolean processFile(final VirtualFile file) {
          if (!file.isDirectory()) {
            for (String indexId : indicesToUpdate) {
              final boolean forceReindexThisProject = requiresRebuild.contains(indexId) && !projectAlreadyProcessed;
              if ((forceReindexThisProject || !IndexingStamp.isFileIndexed(file, indexId)) && getInputFilter(indexId).acceptInput(file)) {
                try {
                  updateSingleIndex(indexId, file, new FileContent(file, loadContent(file)), null);
                }
                catch (StorageException e) {
                  // todo
                }
              }
            }
          }
          return true;
        }
      });
    }
    finally {
      flushStorages();
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (String key : myIndices.keySet()) {
      indicesToDrop.remove(key);
    }
    for (String s : indicesToDrop) {
      final File file = getStorageFile(s);
      final String filename = file.getName();
      final File[] toDelete = file.getParentFile().listFiles(new FileFilter() {
        public boolean accept(final File pathname) {
          if (pathname.isFile()) {
            if (pathname.getName().startsWith(filename)) {
              return true;
            }
          }
          return false;
        }
      });
      for (File f : toDelete) {
        FileUtil.delete(f);
      }
    }
  }

  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(String indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  private InputFilter getInputFilter(String indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }
  
  private static class ProjectContentFilter<T> {
    private final Project myProject;

    public ProjectContentFilter(Project project) {
      myProject = project;
    }

    public List<T> apply(final ValueContainer<T> container) {
      final List<T> valueList = container.toValueList();
      final DirectoryIndex dirIndex = DirectoryIndex.getInstance(myProject);
      final PersistentFS fs = (PersistentFS)PersistentFS.getInstance();
      for (Iterator<T> it = valueList.iterator(); it.hasNext();) {
        final T value = it.next();
        if (!belongsToProject(container.getInputIdsIterator(value), dirIndex, fs)) {
          it.remove();
        }
      }
      return valueList;
    }

    private static boolean belongsToProject(final ValueContainer.IntIterator inputIdsIterator, final DirectoryIndex dirIndex, final PersistentFS fs) {
      while (inputIdsIterator.hasNext()) {
        final int id = inputIdsIterator.next();
        final DirectoryInfo directoryInfo = fs.isDirectory(id)? 
                                            dirIndex.getInfoForDirectoryId(id) : 
                                            dirIndex.getInfoForDirectoryId(fs.getParent(id));
        if (directoryInfo != null && directoryInfo.contentRoot != null) {
          return true; // the directory is under the content
        }
      }
      return false;
    }
  }
  
  private static File getStorageFile(final String indexName) {
    return new File(getPersistenceRoot(), indexName.toLowerCase(Locale.US) + ".vfi");
  }

  private static File getPersistenceRoot() {
    File file = new File(PathManager.getSystemPath(), "index");
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
  }
  
  private void updateIndicesForFile(final VirtualFile file, final @Nullable CharSequence oldContent) {
    final FileContent oldFC = oldContent != null ? new FileContent(file, oldContent) : null;
    final boolean isValidFile = file.isValid();
    FileContent currentFC = null;
    boolean fileContentLoaded = false;
    
    for (String indexKey : myIndices.keySet()) {
      if (!isValidFile || getInputFilter(indexKey).acceptInput(file)) {
        if (!fileContentLoaded) {
          fileContentLoaded = true;
          currentFC = isValidFile ? new FileContent(file, loadContent(file)) : null;
        }
        try {
          updateSingleIndex(indexKey, file, currentFC, oldFC);
        }
        catch (StorageException e) {
          // todo
        }
      }
    }
  }

  private void updateSingleIndex(final String indexId, final VirtualFile file, final FileContent currentFC, final FileContent oldFC) 
    throws StorageException {
    
    final int inputId = Math.abs(((NewVirtualFile)file).getId());
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    index.update(inputId, currentFC, oldFC);
    if (file.isValid()) {
      IndexingStamp.update(file, indexId);
    }
  }

  private static CharSequence loadContent(VirtualFile file) {
    try {
      return LoadTextUtil.getTextByBinaryPresentation(file.contentsToByteArray(), file, false);
    }
    catch (IOException e) {
      return "";
    }
  }

  private static final class CompositeInputFiler implements InputFilter {
    private final Set<InputFilter> myFilters = new HashSet<InputFilter>();
    
    public void addFilter(InputFilter filter) {
      myFilters.add(filter);
    }

    public void removeFilter(InputFilter filter) {
      myFilters.remove(filter);
    }
    
    public boolean acceptInput(final VirtualFile file) {
      for (InputFilter filter : myFilters) {
        if (filter.acceptInput(file)) {
          return true;
        }
      }
      return false;
    }
  }
  
  private static final com.intellij.openapi.util.Key<CharSequence> CONTENT_KEY = com.intellij.openapi.util.Key.create("FileContent");
  private final class MyVFSListener extends VirtualFileAdapter {
    public void contentsChanged(final VirtualFileEvent event) {
      doAfterAction(event);
    }

    public void fileCreated(final VirtualFileEvent event) {
      doAfterAction(event);
    }

    public void fileDeleted(final VirtualFileEvent event) {
      doAfterAction(event);
    }

    public void fileMoved(final VirtualFileMoveEvent event) {
      doAfterAction(event);
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      doAfterAction(event);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      doBeforeAction(event);
    }

    public void beforeContentsChange(final VirtualFileEvent event) {
      doBeforeAction(event);
    }

    public void beforeFileMovement(final VirtualFileMoveEvent event) {
      doBeforeAction(event);
    }
    
    private void doBeforeAction(final VirtualFileEvent event) {
      final VirtualFile file = event.getFile();
      if (myCompositeFilter.acceptInput(file)) {
        final CharSequence oldContent = loadContent(file);
        file.putUserData(CONTENT_KEY, oldContent);
      }
      else {
        file.putUserData(CONTENT_KEY, null);
      }
    }
    
    private void doAfterAction(final VirtualFileEvent event) {
      final VirtualFile file = event.getFile();
      if (myCompositeFilter.acceptInput(file)) {
        final @Nullable CharSequence oldContent = file.getUserData(CONTENT_KEY);
        file.putUserData(CONTENT_KEY, null);
        updateIndicesForFile(file, oldContent);
      }
    }


  }
}
