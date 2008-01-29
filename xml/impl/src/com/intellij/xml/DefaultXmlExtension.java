package com.intellij.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class DefaultXmlExtension extends XmlExtension {
  
  public boolean isAvailable(final XmlFile file) {
    return true;
  }

  public Set<String> getAvailableTagNames(@NotNull final XmlFile context) {
    return Collections.emptySet();
  }

  public Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context) {
    return Collections.emptySet();
  }

  public void insertNamespaceDeclaration(@NotNull final XmlFile file, @NotNull final Editor editor, @NotNull final Set<String> possibleNamespaces,
                                           @Nullable final String nsPrefix,
                                           @Nullable final Consumer<String> runAfter) {
  }
}
