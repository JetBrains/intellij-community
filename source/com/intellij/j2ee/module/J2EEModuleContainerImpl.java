package com.intellij.j2ee.module;

import com.intellij.j2ee.ex.J2EEModulePropertiesEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.watcher.ModuleRootsWatcher;
import com.intellij.openapi.roots.watcher.ModuleRootsWatcherFactory;
import com.intellij.openapi.util.*;
import com.intellij.util.ExternalizableString;
import org.jdom.Element;

import java.util.*;

/**
 * @author Alexey Kudravtsev
 */
public abstract class J2EEModuleContainerImpl
  extends J2EEModulePropertiesEx implements JDOMExternalizable, ModuleByNameProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.module.J2EEModuleContainerImpl");

  private J2EEModuleContainerImpl myModifiableModel;
  protected final Module myParentModule;
  private final Set<ContainerElement> myContents = new LinkedHashSet<ContainerElement>();

  /**
   * @deprecated
   */
  private ModuleRootsWatcher<ExternalizableString> myModuleRootsWatcher;
  private Map<ExternalizableString, OrderEntryInfo> myOrderInfo;
  private static final String NAME_ATTRIBUTE_NAME = "name";
  private static final String LEVEL_ATTRIBUTE_NAME = "level";
  private static final String TYPE_ATTRIBUTE_NAME = "type";
  private static final String CONTAINER_ELEMENT_NAME = "containerElement";
  private static final String MODULE_TYPE = "module";
  private static final String LIBRARY_TYPE = "library";
  private static final String URL_ELEMENT_NAME = "url";

  protected J2EEModuleContainerImpl(Module module) {
    LOG.assertTrue(module != null);
    myParentModule = module;
  }

  protected void initContainer() {
    if (myModuleRootsWatcher != null) {
      migrateRootsWatcher();
    }
  }

  private void migrateRootsWatcher() {
    final Set<ExternalizableString> keys = myOrderInfo.keySet();
    for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
      ExternalizableString key = (ExternalizableString)iterator.next();
      final OrderEntryInfo orderEntryInfo = myOrderInfo.get(key);
      if (!orderEntryInfo.copy) continue;
      final OrderEntry orderEntry = myModuleRootsWatcher.find(getModule(), key);
      if (orderEntry == null) continue;
      final ContainerElement containerElement;
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module module = ((ModuleOrderEntry)orderEntry).getModule();
        if (module == null) continue;
        containerElement = new ModuleLinkImpl(module, getModule());
        containerElement.setPackagingMethod(getModule().getModuleType().equals(ModuleType.EJB)
                                            ? J2EEPackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
                                            : J2EEPackagingMethod.COPY_FILES);
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (library == null) continue;
        containerElement = new LibraryLinkImpl(library, getModule());
        containerElement.setPackagingMethod(getModule().getModuleType().equals(ModuleType.EJB)
                                            ? J2EEPackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
                                            : J2EEPackagingMethod.COPY_FILES);
      }
      else {
        LOG.error("invalid type " + orderEntry);
        continue;
      }
      containerElement.setURI(orderEntryInfo.URI);
      final Map<String, String> attributes = orderEntryInfo.getAttributes();
      for (Iterator iterator1 = attributes.keySet().iterator(); iterator1.hasNext();) {
        String name = (String)iterator1.next();
        String value = attributes.get(name);
        containerElement.setAttribute(name, value);
      }
      containerElement.setURI(orderEntryInfo.URI);
      addElement(containerElement);
    }


  }

  public void readExternal(Element element) throws InvalidDataException {
    clearContainer();
    final List children = element.getChildren(CONTAINER_ELEMENT_NAME);
    for (int i = 0; i < children.size(); i++) {
      Element child = (Element)children.get(i);
      final String type = child.getAttributeValue(TYPE_ATTRIBUTE_NAME);
      ContainerElement containerElement = null;
      if (MODULE_TYPE.equals(type)) {
        String moduleName = child.getAttributeValue(NAME_ATTRIBUTE_NAME);
        if (moduleName != null) {
          containerElement = new ModuleLinkImpl(moduleName, getModule());
        }
      }
      else if (LIBRARY_TYPE.equals(type)) {
        Library library = findLibrary(child);
        if (library != null) {
          containerElement = new LibraryLinkImpl(library, getModule());
        }
        else {
          String libraryLevel = child.getAttributeValue(LEVEL_ATTRIBUTE_NAME);
          if (LibraryLink.MODULE_LEVEL.equals(libraryLevel)) {
            containerElement = new LibraryLinkImpl(null, getModule());
          }
        }
      }
      else {
        throw new InvalidDataException("invalid type: " + type + " " + child);
      }
      if (containerElement != null) {
        containerElement.readExternal(child);
        addElement(containerElement);
      }

    }
    if (children.size() == 0) {
      readPlainOldWatcherEntries(element);
    }
  }

  private Library findLibrary(Element child) {
    String libraryName = child.getAttributeValue(NAME_ATTRIBUTE_NAME);
    String libraryLevel = child.getAttributeValue(LEVEL_ATTRIBUTE_NAME);
    if (LibraryLink.MODULE_LEVEL.equals(libraryLevel)) {
      Element urlElement = child.getChild(URL_ELEMENT_NAME);
      if (urlElement == null) return null;
      return LibraryLink.findModuleLibrary(getModule(), urlElement.getText());
    }
    else {
      return LibraryLink.findLibrary(libraryName, libraryLevel, getModule().getProject());
    }

  }

  private static class ExternalizableStringFactory implements Factory<ExternalizableString> {
    private int seed;

    public ExternalizableString create() {
      return new ExternalizableString("" + seed++);
    }

    private void expandSeed(ExternalizableString orderEntryKey) {
      try {
        int i = Integer.parseInt(orderEntryKey.value);
        seed = Math.max(i + 1, seed);
      }
      catch (NumberFormatException e) {
        // must be syntetic entry
      }
    }
  }

  /**
   * @deprecated
   */
  private void readPlainOldWatcherEntries(Element element) throws InvalidDataException {
    final Element watcher = element.getChild("orderEntriesWatcher");
    if (watcher == null) {
      return;
    }
    final ExternalizableStringFactory factory = new ExternalizableStringFactory();
    myModuleRootsWatcher = ModuleRootsWatcherFactory.create(factory);
    myModuleRootsWatcher.readExternal(watcher);
    myOrderInfo = new HashMap<ExternalizableString, OrderEntryInfo>();
    final Element infoRoot = element.getChild("order-entry-info");
    if (infoRoot != null) {
      final List infos = infoRoot.getChildren("info");
      for (int i = 0; i < infos.size(); i++) {
        Element info = (Element)infos.get(i);
        final Element keyElement = info.getChild("key");
        final ExternalizableString key = new ExternalizableString("");
        key.readExternal(keyElement);
        final Element valueElement = info.getChild("value");
        final OrderEntryInfo value = new OrderEntryInfo();
        value.readExternal(valueElement);
        // the only situation we want to change seed
        factory.expandSeed(key);

        myOrderInfo.put(key, value);
      }
    }

  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!isActive()) return;

    for (Iterator iterator = myContents.iterator(); iterator.hasNext();) {
      ContainerElement containerElement = (ContainerElement)iterator.next();
      final Element child = new Element(CONTAINER_ELEMENT_NAME);
      if (containerElement instanceof ModuleLink) {
        child.setAttribute(TYPE_ATTRIBUTE_NAME, MODULE_TYPE);
        child.setAttribute(NAME_ATTRIBUTE_NAME, ((ModuleLink)containerElement).getName());
      }
      else if (containerElement instanceof LibraryLink) {
        child.setAttribute(TYPE_ATTRIBUTE_NAME, LIBRARY_TYPE);
        LibraryLink libraryLink = (LibraryLink)containerElement;
        String level = libraryLink.getLevel();
        child.setAttribute(LEVEL_ATTRIBUTE_NAME, level);
      }
      else {
        throw new WriteExternalException("invalid type: " + child);
      }
      containerElement.writeExternal(child);
      element.addContent(child);
    }
  }

  protected boolean isActive() {
    return getInstance(getModule()) == this;
  }

  public ModuleLink[] getContainingModules() {

    final List<ModuleLink> moduleLinks = new ArrayList<ModuleLink>();

    ContainerElement[] elements = getElements();
    for (int i = 0; i < elements.length; i++) {
      ContainerElement element = elements[i];
      if (element instanceof ModuleLink) {
        moduleLinks.add((ModuleLink)element);
      }
    }

    return moduleLinks.toArray(new ModuleLink[moduleLinks.size()]);
  }

  public LibraryLink[] getContainingLibraries() {
    final List<LibraryLink> libraryLinks = new ArrayList<LibraryLink>();
    ContainerElement[] elements = getElements();
    for (int i = 0; i < elements.length; i++) {
      ContainerElement element = elements[i];
      if (element instanceof LibraryLink) {
        libraryLinks.add((LibraryLink)element);
      }
    }
    return libraryLinks.toArray(new LibraryLink[libraryLinks.size()]);
  }

  public ContainerElement[] getElements() {
    return getElements(this);
  }

  public ContainerElement[] getElements(ModuleByNameProvider provider) {
    ArrayList<ContainerElement> result = new ArrayList<ContainerElement>();
    for (Iterator iterator = myContents.iterator(); iterator.hasNext();) {
      ContainerElement containerElement = (ContainerElement)iterator.next();
      if (((ResolvableElement)containerElement).resolveElement(provider)) {
        result.add(containerElement);
      }
    }
    return result.toArray(new ContainerElement[result.size()]);
  }

  public void setElements(ContainerElement[] elements) {
    clearContainer();
    myContents.addAll(Arrays.asList(elements));
  }

  public void removeModule(Module module) {
    for (Iterator iterator = myContents.iterator(); iterator.hasNext();) {
      ContainerElement element = (ContainerElement)iterator.next();
      if (element instanceof ModuleLink && ((ModuleLink)element).getModule() == module) {
        myContents.remove(element);
        break;
      }
    }
  }


  public Module getModule() {
    return myParentModule;
  }

  private void clearContainer() {
    myContents.clear();
  }

  public void startEdit(ModifiableRootModel rootModel) {
    myModifiableModel = createInstance(rootModel);
    myModifiableModel.copyFrom(this, rootModel);

    myModifiableModel.initModifiableModel();
  }

  protected abstract void initModifiableModel();

  protected abstract J2EEModuleContainerImpl createInstance(ModifiableRootModel rootModel);

  protected void copyFrom(J2EEModuleContainer from, ModifiableRootModel rootModel) {
    copyContainerInfoFrom((J2EEModuleContainerImpl)from);
  }

  public final J2EEModuleContainer getModifiableModel() {
    return myModifiableModel;
  }

  public void commit(ModifiableRootModel model) throws ConfigurationException {
    if (isModified(model)) {
      final J2EEModuleContainerImpl modified = (J2EEModuleContainerImpl)getModifiableModel();
      copyContainerInfoFrom(modified);
      containedEntriesChanged();
    }
  }

  public boolean isModified(ModifiableRootModel model) {
    final J2EEModuleContainer modified = getModifiableModel();
    final ContainerElement[] modifiedElements = modified.getElements();

    return !Arrays.equals(modifiedElements, getElements());
  }

  private void copyContainerInfoFrom(J2EEModuleContainerImpl from) {
    clearContainer();
    final ContainerElement[] elements = from.getElements();
    for (int i = 0; i < elements.length; i++) {
      final ContainerElement element = elements[i];
      addElement(element.clone());
    }
  }

  public void addElement(ContainerElement element) {
    myContents.add(element);
  }

  public Module findModule(final String name) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      public Module compute() {
        return ModuleManager.getInstance(getModule().getProject()).findModuleByName(name);
      }
    });
  }
}
