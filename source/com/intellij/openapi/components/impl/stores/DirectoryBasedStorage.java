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
import com.intellij.openapi.vfs.VirtualFile;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

//todo: support missing plugins
//todo: support storage data
public class DirectoryBasedStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DirectoryBasedStorage");

  private TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private IFile myDir;
  private StateSplitter mySplitter;

  private Map<String, Map<IFile, Element>> myStates = null;
  private Object mySession;

  public DirectoryBasedStorage(final TrackingPathMacroSubstitutor pathMacroSubstitutor, final String dir, final StateSplitter splitter,
                               Disposable parentDisposable) {
    assert dir.indexOf("$") < 0;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = FILE_SYSTEM.createFile(dir);
    mySplitter = splitter;
    Disposer.register(parentDisposable, this);
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    if (myStates == null) loadState();

    final Map<IFile, Element> statesMap = myStates.get(componentName);
    if (statesMap == null) return null;

    List<Element> subElements = new ArrayList<Element>();

    for (Element e : statesMap.values()) {
      final List children = e.getChildren();
      assert children.size() == 1;
      final Element subElement = (Element)children.get(0);
      subElement.detach();
      subElements.add(subElement);
    }

    final Element state = new Element("component");
    mySplitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
    myStates.remove(componentName);

    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(state);
    }

    return DefaultStateSerializer.deserializeState(state, stateClass, mergeInto);
  }

  private void loadState() throws StateStorageException {
    myStates = new HashMap<String, Map<IFile, Element>>();
    if (!myDir.exists()) {
      return;
    }
    try {
      final IFile[] files = myDir.listFiles();

      for (IFile file : files) {
        final Document document = JDOMUtil.loadDocument(file);
        final Element element = document.getRootElement();
        assert element.getName().equals("component");

        String componentName = element.getAttributeValue("name");
        assert componentName != null;
        Map<IFile, Element> stateMap = myStates.get(componentName);
        if (stateMap == null) {
          stateMap = new HashMap<IFile, Element>();
          myStates.put(componentName, stateMap);
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
  }

  public void setState(final String componentName, Object state, final Storage storageSpec) throws StateStorageException {
    if (myStates == null) myStates = new HashMap<String, Map<IFile, Element>>();
    try {
      final Element element = DefaultStateSerializer.serializeState(state, storageSpec);

      if (myPathMacroSubstitutor != null) {
        myPathMacroSubstitutor.collapsePaths(element);
      }

      final List<Pair<Element, String>> states = mySplitter.splitState(element);

      Map<IFile, Element> stateMap = myStates.get(componentName);
      if (stateMap == null) {
        stateMap = new HashMap<IFile, Element>();
        myStates.put(componentName, stateMap);
      }

      for (Pair<Element, String> pair : states) {
        Element e = pair.first;
        String name = pair.second;

        Element statePart = new Element("component");
        statePart.setAttribute("name", componentName);
        e.detach();
        statePart.addContent(e);


        stateMap.put(myDir.getChild(name), statePart);
      }
    }
    catch (ParserConfigurationException e) {
      throw new StateStorageException(e);
    }
    catch (WriteExternalException e) {
      throw new StateStorageException(e);
    }
  }

  public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
    if (myStates == null) loadState();
    return myStates.containsKey(componentName);
  }

  public List<VirtualFile> getAllStorageFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String componentName : myStates.keySet()) {
      Map<IFile, Element> stateMap = myStates.get(componentName);

      for (IFile file : stateMap.keySet()) {
        final VirtualFile virtualFile = StorageUtil.getVirtualFile(file);
        if (virtualFile != null) {
          result.add(virtualFile);
        }
      }
    }


    return result;
  }

  public boolean needsSave() throws StateStorageException {
    try {
      if (!myDir.exists()) return true;
      assert myDir.isDirectory();

      IFile[] children = myDir.listFiles();

      Set<String> currentNames = new HashSet<String>();

      for (IFile child : children) {
        currentNames.add(child.getName());
      }

      for (String componentName : myStates.keySet()) {
        Map<IFile, Element> stateMap = myStates.get(componentName);

        for (IFile file : stateMap.keySet()) {
          if (!currentNames.contains(file.getName())) return true;
          currentNames.remove(file.getName());

          final byte[] text = StorageUtil.printElement(stateMap.get(file));
          if (!Arrays.equals(file.loadBytes(), text)) return true;
        }
      }

      return !currentNames.isEmpty();
    }
    catch (IOException e) {
      LOG.debug(e);
      return true;
    }
  }

  public void save() throws StateStorageException {
    final Set<String> currentNames = new HashSet<String>();

    if (!myDir.exists()) {
      myDir.createParentDirs();
      myDir.mkDir();
    }

    IFile[] children = myDir.listFiles();
    for (IFile child : children) {
      currentNames.add(child.getName());
    }

    for (String componentName : myStates.keySet()) {
      Map<IFile, Element> stateMap = myStates.get(componentName);

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
          assert virtualFile != null;
          try {
            virtualFile.delete(DirectoryBasedStorage.this);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    });

    myStates.clear();
  }

  public Set<String> getUsedMacros() {
    throw new UnsupportedOperationException("Method getUsedMacros not implemented in " + getClass());
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    assert mySession == null;
    final ExternalizationSession session = new ExternalizationSession() {
      public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec) throws StateStorageException {
        assert mySession == this;
        DirectoryBasedStorage.this.setState(componentName, state, storageSpec);
      }
    };

    mySession = session;
    return session;
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    return new SaveSession() {
      public boolean needsSave() throws StateStorageException {
        assert mySession == this;
        return DirectoryBasedStorage.this.needsSave();
      }

      public void save() throws StateStorageException {
        assert mySession == this;
        DirectoryBasedStorage.this.save();
      }

      public Set<String> getUsedMacros() {
        assert mySession == this;
        return DirectoryBasedStorage.this.getUsedMacros();
      }
    };
  }

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession = null;
  }

  public void dispose() {
  }
}
