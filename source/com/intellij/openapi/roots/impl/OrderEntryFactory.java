package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 *  @author dsl
 */
public class OrderEntryFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEntryFactory");
  public @NonNls static final String ORDER_ENTRY_ELEMENT_NAME = "orderEntry";
  public @NonNls static final String ORDER_ENTRY_TYPE_ATTR = "type";

  static OrderEntry createOrderEntryByElement(Element element,
                                              RootModelImpl rootModel,
                                              ProjectRootManagerImpl projectRootManager,
                                              VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    LOG.assertTrue(ORDER_ENTRY_ELEMENT_NAME.equals(element.getName()));
    final String type = element.getAttributeValue(ORDER_ENTRY_TYPE_ATTR);
    if (type == null) {
      throw new InvalidDataException();
    }
    if (ModuleSourceOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleSourceOrderEntryImpl(element, rootModel);
    }
    else if (ModuleJdkOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleJdkOrderEntryImpl(element, rootModel, projectRootManager, filePointerManager);
    }
    else if (InheritedJdkOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new InheritedJdkOrderEntryImpl(element, rootModel, projectRootManager, filePointerManager);
    }
    else if (LibraryOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new LibraryOrderEntryImpl(element, rootModel, projectRootManager, filePointerManager);
    }
    else if (ModuleLibraryOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleLibraryOrderEntryImpl(element, rootModel, projectRootManager, filePointerManager);
    }
    else if (ModuleOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleOrderEntryImpl(element, rootModel);
    }
    else throw new InvalidDataException("Unknown order entry type:" + type);
  }

  static Element createOrderEntryElement(String type) {
    final Element element = new Element(ORDER_ENTRY_ELEMENT_NAME);
    element.setAttribute(ORDER_ENTRY_TYPE_ATTR, type);
    return element;
  }

}
