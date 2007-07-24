package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.*;

//todo: support missing plugins
//todo: support storage data
public class DirectoryBasedStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DirectoryBasedStorage");

  private TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private IFile myDir;
  private StateSplitter mySplitter;

  private Object mySession;
  private MyStorageData myStorageData = null;
  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String NAME = "name";

  public DirectoryBasedStorage(final TrackingPathMacroSubstitutor pathMacroSubstitutor, final String dir, final StateSplitter splitter,
                               Disposable parentDisposable, final PicoContainer picoContainer) {
    assert dir.indexOf("$") < 0;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = FILE_SYSTEM.createFile(dir);
    mySplitter = splitter;
    Disposer.register(parentDisposable, this);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);


    if (virtualFileTracker != null && messageBus != null) {
      final String path = myDir.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL + "://" + path.replace(File.separatorChar, '/');


      final Listener listener = messageBus.syncPublisher(StateStorage.STORAGE_TOPIC);
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        public void contentsChanged(final VirtualFileEvent event) {
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        public void fileDeleted(final VirtualFileEvent event) {
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        public void fileCreated(final VirtualFileEvent event) {
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }
      }, true, this);
    }
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    if (myStorageData == null)
      myStorageData = loadState();

    final Map<IFile, Element> statesMap = myStorageData.myStates.get(componentName);
    if (statesMap == null) {
      return DefaultStateSerializer.deserializeState(new Element(COMPONENT), stateClass, mergeInto);
    }

    List<Element> subElements = new ArrayList<Element>();

    for (Element e : statesMap.values()) {
      final List children = e.getChildren();
      assert children.size() == 1;
      final Element subElement = (Element)children.get(0);
      subElement.detach();
      subElements.add(subElement);
    }

    final Element state = new Element(COMPONENT);
    mySplitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
    myStorageData.myStates.remove(componentName);

    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(state);
    }

    return DefaultStateSerializer.deserializeState(state, stateClass, mergeInto);
  }

  private MyStorageData loadState() throws StateStorageException {
    MyStorageData storageData = new MyStorageData();
    storageData.myStates = new HashMap<String, Map<IFile, Element>>();
    if (!myDir.exists()) {
      return storageData;
    }
    try {
      final IFile[] files = myDir.listFiles();

      for (IFile file : files) {
        final Document document = JDOMUtil.loadDocument(file);
        final Element element = document.getRootElement();
        assert element.getName().equals(COMPONENT);

        String componentName = element.getAttributeValue(NAME);
        assert componentName != null;
        Map<IFile, Element> stateMap = storageData.myStates.get(componentName);
        if (stateMap == null) {
          stateMap = new HashMap<IFile, Element>();
          storageData.myStates.put(componentName, stateMap);
        }

        stateMap.put(file, element);
      }
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
    catch (JDOMException e) {
      throw new StateStorageException(e);
    }

    return storageData;
  }


  public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
    if (!myDir.exists()) return false;
    return true;
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    if (myStorageData == null) {
      try {
        myStorageData = loadState();
      }
      catch (StateStorageException e) {
        LOG.error(e);
      }
    }
    final ExternalizationSession session = new MyExternalizationSession(myPathMacroSubstitutor, myStorageData.clone());

    mySession = session;
    return session;
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final MySaveSession session = new MySaveSession(((MyExternalizationSession)externalizationSession).myStorageData, myPathMacroSubstitutor);
    mySession = session;
    return session;
  }

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession = null;
  }

  public void reload(final Set<String> changedComponents) throws StateStorageException {
    myStorageData = loadState();

  }

  public void dispose() {
  }

  private class MySaveSession implements SaveSession {
    private final MyStorageData myStorageData;
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

    private MySaveSession(final MyStorageData storageData, final TrackingPathMacroSubstitutor pathMacroSubstitutor) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
    }

    public Set<String> getUsedMacros() {
      if (myPathMacroSubstitutor != null) {
        return myPathMacroSubstitutor.getUsedMacros();
      }
      else {
        return Collections.emptySet();
      }
    }

    public void save() throws StateStorageException {
      assert mySession == this;
      final Set<String> currentNames = new HashSet<String>();

      if (!myDir.exists()) {
        myDir.createParentDirs();
        myDir.mkDir();
      }

      IFile[] children = myDir.listFiles();
      for (IFile child : children) {
        currentNames.add(child.getName());
      }

      for (String componentName : myStorageData.myStates.keySet()) {
        Map<IFile, Element> stateMap = myStorageData.myStates.get(componentName);

        for (IFile file : stateMap.keySet()) {
          StorageUtil.save(file, stateMap.get(file));
          currentNames.remove(file.getName());
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (String name : currentNames) {
            IFile child = myDir.getChild(name);
            final VirtualFile virtualFile = StorageUtil.getVirtualFile(child);
            assert virtualFile != null : "Can't find vFile for: " + child;
            try {
              virtualFile.delete(DirectoryBasedStorage.this);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        }
      });

      myStorageData.myStates.clear();
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      boolean containsSelf = false;

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == DirectoryBasedStorage.this) {
          containsSelf = true;
          break;
        }
      }

      if (!containsSelf) return Collections.emptySet();

      Set<String> componentNames = new HashSet<String>();
      componentNames.addAll(myStorageData.myStates.keySet());

      return componentNames;
    }

    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      assert mySession == this;

      try {
        if (!myDir.exists()) return getAllStorageFiles();
        assert myDir.isDirectory();


        List<IFile> filesToSave = new ArrayList<IFile>();

        IFile[] children = myDir.listFiles();

        Set<String> currentChildNames = new HashSet<String>();

        for (IFile child : children) {
          currentChildNames.add(child.getName());
        }

        for (String componentName : myStorageData.myStates.keySet()) {
          Map<IFile, Element> stateMap = myStorageData.myStates.get(componentName);

          for (IFile file : stateMap.keySet()) {
            if (!currentChildNames.contains(file.getName())) {
              continue;
            }

            currentChildNames.remove(file.getName());

            final byte[] text = StorageUtil.printElement(stateMap.get(file));
            if (!Arrays.equals(file.loadBytes(), text)) {
              filesToSave.add(file);
            }
          }
        }

        for (String childName : currentChildNames) {
          final IFile child = myDir.getChild(childName);
          filesToSave.add(child);
        }

        return filesToSave;
      }
      catch (IOException e) {
        LOG.debug(e);
      }

      return Collections.emptyList();
    }

    public List<IFile> getAllStorageFiles() {
      List<IFile> allStorageFiles = new ArrayList<IFile>();
      for (String componentName : myStorageData.myStates.keySet()) {
          Map<IFile, Element> stateMap = myStorageData.myStates.get(componentName);

        for (IFile file : stateMap.keySet()) {
          allStorageFiles.add(file);
        }
      }

      return allStorageFiles;
    }
  }

  private class MyStorageData {
    private Map<String, Map<IFile, Element>> myStates = new HashMap<String, Map<IFile, Element>>();

    protected MyStorageData clone() {
      final MyStorageData result = new MyStorageData();
      result.myStates = new HashMap<String, Map<IFile, Element>>(myStates);
      return result;
    }
  }

  private class MyExternalizationSession implements ExternalizationSession {
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
    private final MyStorageData myStorageData;

    private MyExternalizationSession(final TrackingPathMacroSubstitutor pathMacroSubstitutor, final MyStorageData storageData) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
      myPathMacroSubstitutor.reset();
    }

    public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec) throws
                                                                                                                            StateStorageException {
      assert mySession == this;
      setState(componentName, state, storageSpec);
    }

    private void setState(final String componentName, Object state, final Storage storageSpec) throws StateStorageException {
      if (myStorageData.myStates == null) myStorageData.myStates = new HashMap<String, Map<IFile, Element>>();
      try {
        final Element element = DefaultStateSerializer.serializeState(state, storageSpec);

        if (myPathMacroSubstitutor != null) {
          myPathMacroSubstitutor.collapsePaths(element);
        }

        final List<Pair<Element, String>> states = mySplitter.splitState(element);

        Map<IFile, Element> stateMap = myStorageData.myStates.get(componentName);
        if (stateMap == null) {
          stateMap = new HashMap<IFile, Element>();
          myStorageData.myStates.put(componentName, stateMap);
        }

        for (Pair<Element, String> pair : states) {
          Element e = pair.first;
          String name = pair.second;

          Element statePart = new Element(COMPONENT);
          statePart.setAttribute(NAME, componentName);
          e.detach();
          statePart.addContent(e);


          stateMap.put(myDir.getChild(name), statePart);
        }
      }
      catch (WriteExternalException e) {
        throw new StateStorageException(e);
      }
    }
  }
}
