package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTableUtil;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 *  @author dsl
 */
public class LibraryTableImplUtil extends LibraryTableUtil {
  @NonNls public static final String MODULE_LEVEL = "module";

  public static Library loadLibrary(Element rootElement, final LibraryTable libraryTable) throws InvalidDataException {
    final LibraryImpl library = new LibraryImpl(libraryTable);
    final List children = rootElement.getChildren(LibraryImpl.ELEMENT);
    if (children.size() != 1) throw new InvalidDataException();
    library.readExternal((Element) children.get(0));
    return library;
  }

  public static Library createModuleLevelLibrary() {
    return new LibraryImpl();
  }

  public static Library createModuleLevelLibrary(String name) {
    return new LibraryImpl(name, null);
  }
}
