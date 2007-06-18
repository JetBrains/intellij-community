package com.intellij.openapi.roots.impl.storage;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nls;
import org.jdom.Element;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;

import java.io.IOException;

/**
 * @author Vladislav.Kaznacheev
*/
public interface ClasspathStorageProvider {

  @NonNls
  String getID ();

  @Nls
  String getDescription ();

  void assertCompatible(final ModifiableRootModel model) throws ConfigurationException;

  void cancel (Module module);

  ClasspathConverter createConverter(Module module);

  interface ClasspathConverter {

    FileSet getFileSet();

    void getClasspath(Element element) throws IOException, InvalidDataException;

    void setClasspath(Element element) throws IOException, WriteExternalException;
  }

  class UnsupportedStorageProvider implements ClasspathStorageProvider {
    private final String myType;

    public UnsupportedStorageProvider(final String type) {
      myType = type;
    }

    @NonNls
    public String getID() {
      return myType;
    }

    @Nls
    public String getDescription() {
      return "Unsupported classpath format " + myType;
    }

    public void assertCompatible(final ModifiableRootModel model) throws ConfigurationException {
      throw new UnsupportedOperationException(getDescription());
    }

    public void cancel(final Module module) {
      throw new UnsupportedOperationException(getDescription());
    }

    public ClasspathConverter createConverter(final Module module) {
      return new ClasspathConverter() {
        public FileSet getFileSet() {
          throw new UnsupportedOperationException(getDescription());
        }

        public void getClasspath(final Element element) throws IOException, InvalidDataException {
          throw new InvalidDataException(getDescription());
        }

        public void setClasspath(final Element element) throws IOException, WriteExternalException {
          throw new WriteExternalException(getDescription());
        }
      };
    }
  }
}
