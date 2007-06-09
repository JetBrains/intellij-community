package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.StringInterner;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

abstract class XmlElementStorage implements StateStorage, Disposable {
  @NonNls private static final Set<String> OBSOLETE_COMPONENT_NAMES = new HashSet<String>(Arrays.asList(
    "Palette"
  ));
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String ATTR_NAME = "name";
  @NonNls private static final String NAME = ATTR_NAME;

  protected TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private @NotNull final String myRootElementName;
  private Set<String> myUsedMacros;
  private Object mySession;
  private StorageData myLoadedData;
  private static StringInterner ourInterner = new StringInterner();

  protected XmlElementStorage(
    @Nullable final TrackingPathMacroSubstitutor pathMacroSubstitutor,
    @NotNull Disposable parentDisposable,
    @NotNull String rootElementName) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myRootElementName = rootElementName;
    Disposer.register(parentDisposable, this);
  }

  @Nullable
  protected abstract Document loadDocument() throws StateStorage.StateStorageException;

  @Nullable
  private synchronized Element getState(final String componentName) throws StateStorage.StateStorageException {
    final StorageData storageData = getStorageData();
    final Element state = storageData.getState(componentName);

    if (state != null) {
      storageData.removeState(componentName);
    }

    return state;
  }

  public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
    final StorageData storageData = getStorageData();
    return storageData.getState(componentName) != null;
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    final Element element = getState(componentName);
    return DefaultStateSerializer.deserializeState(element, stateClass, mergeInto);
  }

  @NotNull
  protected StorageData getStorageData() throws StateStorageException {
    if (myLoadedData != null) return myLoadedData;

    myLoadedData = loadData();

    return myLoadedData;
  }

  private StorageData loadData() throws StateStorageException {
    StorageData result = createStorageData();

    final Document document = loadDocument();

    if (document != null) {
      loadState(result, document.getRootElement());
    }

    return result;
  }

  private void loadState(final StorageData result, final Element element) throws StateStorageException {

    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(element);
    }

    JDOMUtil.internElement(element, ourInterner);

    try {
      result.load(element);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  protected StorageData createStorageData() {
    return new StorageData(myRootElementName);
  }

  public void setDefaultState(final Element element) {
    myLoadedData = createStorageData();
    try {
      loadState(myLoadedData, element);
    }
    catch (StateStorageException e) {
      LOG.error(e);
    }
  }

  public Set<String> getUsedMacros() {
    return myUsedMacros;
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    try {
      assert mySession == null;

      final ExternalizationSession session = new MyExternalizationSession(getStorageData().clone());

      mySession = session;
      return session;
    }
    catch (StateStorageException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession saveSession = createSaveSession((MyExternalizationSession)externalizationSession);
    mySession = saveSession;
    return saveSession;
  }

  protected abstract MySaveSession createSaveSession(final MyExternalizationSession externalizationSession);

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession = null;
  }

  protected class MyExternalizationSession implements ExternalizationSession {
    private StorageData myStorageData;

    public MyExternalizationSession(final StorageData storageData) {
      myStorageData = storageData;
    }

    public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec) throws StateStorageException {
      assert mySession == this;

      try {
        setState(componentName,  DefaultStateSerializer.serializeState(state, storageSpec));
      }
      catch (ParserConfigurationException e) {
        LOG.error(e);
      }
      catch (WriteExternalException e) {
        LOG.debug(e);
      }
    }

    private synchronized void setState(final String componentName, final Element element) throws StateStorageException {
      if (element.getAttributes().isEmpty() && element.getChildren().isEmpty()) return;

      myStorageData.setState(componentName, element);
    }
  }

  protected abstract class MySaveSession implements SaveSession {
    StorageData myStorageData;
    private Document myDocumentToSave;

    public MySaveSession(MyExternalizationSession externalizationSession) {
      myStorageData = externalizationSession.myStorageData;
    }

    public final boolean needsSave() throws StateStorageException {
      assert mySession == this;
      return _needsSave();
    }

    protected abstract boolean _needsSave() throws StateStorageException;
    protected abstract void doSave() throws StateStorage.StateStorageException;

    public final void save() throws StateStorageException {
      assert mySession == this;

      if (!_needsSave()) return;
      doSave();
    }

    public Set<String> getUsedMacros() throws StateStorageException {
      assert mySession == this;

      if (myUsedMacros == null) {
        if (myPathMacroSubstitutor != null) {
          myPathMacroSubstitutor.reset();
          final Map<String, Element> states = myStorageData.myComponentStates;

          for (Element e : states.values()) {
            myPathMacroSubstitutor.collapsePaths((Element)e.clone());
          }

          myUsedMacros = new HashSet<String>(myPathMacroSubstitutor.getUsedMacros());
        }
        else {
          myUsedMacros = new HashSet<String>();
        }
      }

      return myUsedMacros;
    }

    protected Document getDocumentToSave() throws StateStorageException {
      if (myDocumentToSave != null) return myDocumentToSave;

      final Element element = myStorageData.save();
      myDocumentToSave = new Document(element);

      if (myPathMacroSubstitutor != null) {
        myPathMacroSubstitutor.reset();
        myPathMacroSubstitutor.collapsePaths(element);
      }

      return myDocumentToSave;
    }

    public StorageData getData() {
      return myStorageData;
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<VirtualFile> changedFiles) {
      try {
        final StorageData storageData = loadData();

        return storageData.getDifference(myStorageData);
      }
      catch (StateStorageException e) {
        LOG.info(e);
      }

      return null;
    }
  }

  public void dispose() {
  }

  protected static class StorageData {
    private final Map<String, Element> myComponentStates;
    protected final String myRootElementName;
    private Integer myHash;

    public StorageData(final String rootElementName) {
      myComponentStates = new TreeMap<String, Element>();
      myRootElementName = rootElementName;
    }

    protected StorageData(StorageData storageData) {
      myRootElementName = storageData.myRootElementName;
      myComponentStates = new TreeMap<String, Element>(storageData.myComponentStates);
    }

    protected void load(@NotNull Element rootElement) throws IOException {
      final Element[] elements = JDOMUtil.getElements(rootElement);
      for (Element element : elements) {
        if (element.getName().equals(COMPONENT)) {
          final String name = element.getAttributeValue(NAME);

          if (name == null) {
            LOG.error("Broken file");
            continue;
          }

          if (OBSOLETE_COMPONENT_NAMES.contains(name)) continue;

          element.detach();

          if (element.getAttributes().size() > 1 || !element.getChildren().isEmpty()) {
            myComponentStates.put(name, element);
          }
        }
      }
    }

    @NotNull
    protected Element save() {
      Element rootElement = new Element(myRootElementName);

      for (String componentName : myComponentStates.keySet()) {
        final Element element = myComponentStates.get(componentName);
        rootElement.addContent((Element)element.clone());
      }

      return rootElement;
    }

    @Nullable
    public Element getState(final String name) {
      final Element e = myComponentStates.get(name);
      if (e != null) {
        e.removeAttribute(NAME);
      }
      return e;
    }

    public void removeState(final String componentName) {
      myComponentStates.remove(componentName);
      clearHash();
    }

    public void setState(final String componentName, final Element element) {
      element.setName(COMPONENT);

      //componentName should be first!
      final List attributes = new ArrayList(element.getAttributes());
      for (Object attribute : attributes) {
        Attribute attr = (Attribute)attribute;
        element.removeAttribute(attr);
      }

      element.setAttribute(NAME, componentName);

      for (Object attribute : attributes) {
        Attribute attr = (Attribute)attribute;
        element.setAttribute(attr.getName(), attr.getValue());
      }

      myComponentStates.put(componentName, element);
      clearHash();
    }

    public StorageData clone() {
      return new StorageData(this);
    }

    public final int getHash() {
      if (myHash == null) {
        myHash = computeHash();
      }
      return myHash.intValue();
    }

    protected int computeHash() {
      int result = 0;

      for (String name : myComponentStates.keySet()) {
        result = 31*result + name.hashCode();
        result = 31*result + JDOMUtil.getTreeHash(myComponentStates.get(name));
      }

      return result;
    }

    protected void clearHash() {
      myHash = null;
    }

    public Set<String> getDifference(final StorageData storageData) {
      Set<String> bothStates = new HashSet<String>(myComponentStates.keySet());
      bothStates.retainAll(storageData.myComponentStates.keySet());

      Set<String> diffs = new HashSet<String>();
      diffs.addAll(storageData.myComponentStates.keySet());
      diffs.addAll(myComponentStates.keySet());
      diffs.removeAll(bothStates);

      for (String componentName : bothStates) {
        final Element e1 = myComponentStates.get(componentName);
        final Element e2 = storageData.myComponentStates.get(componentName);

        if (!JDOMUtil.areElementsEqual(e1, e2)) {
          diffs.add(componentName);
        }
      }


      return diffs;
    }
  }

  public void reload(final Set<String> changedComponents) throws StateStorageException {
    final StorageData storageData = loadData();

    final StorageData oldLoadedData = myLoadedData;

    Set<String> componentsToRetain = new HashSet<String>(oldLoadedData.myComponentStates.keySet());
    componentsToRetain.addAll(changedComponents);

    storageData.myComponentStates.keySet().retainAll(componentsToRetain);
    myLoadedData = storageData;
  }
}
