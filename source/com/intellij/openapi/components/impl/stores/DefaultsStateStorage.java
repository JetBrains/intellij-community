package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DOMUtil;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;

class DefaultsStateStorage implements StateStorage {
  private PathMacroManager myPathMacroManager;


  public DefaultsStateStorage(@Nullable final PathMacroManager pathMacroManager) {
    myPathMacroManager = pathMacroManager;
  }

  @Nullable
  public Element getState(final Object component, final String componentName) throws StateStorageException {
    final URL url = DecodeDefaultsUtil.getDefaults(component, componentName);
    if (url == null) return null;

    try {
      final Document document = DOMUtil.load(url);
      final Element documentElement = document.getDocumentElement();

      if (myPathMacroManager != null) {
        myPathMacroManager.expandPaths(documentElement);
      }

      return documentElement;
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
    catch (ParserConfigurationException e) {
      throw new StateStorageException(e);
    }
    catch (SAXException e) {
      throw new StateStorageException(e);
    }
  }

  public void setState(final Object component, final String componentName, final Element domElement) {
    throw new UnsupportedOperationException("Method setState is not supported in " + getClass());
  }

  public void save() {
    throw new UnsupportedOperationException("Method save is not supported in " + getClass());
  }

  public boolean needsSave() throws StateStorageException {
    throw new UnsupportedOperationException("Method needsSave is not supported in " + getClass());
  }

  public List<VirtualFile> getAllStorageFiles() {
    throw new UnsupportedOperationException("Method getAllStorageFiles is not supported in " + getClass());
  }
}
