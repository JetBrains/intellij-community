package com.intellij.xml;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlExtension {

  private static final ExtensionPointName<XmlExtension> EP_NAME = new ExtensionPointName<XmlExtension>("com.intellij.xml.xmlExtension");

  private static final XmlExtension DEFAULT_EXTENSION = new DefaultXmlExtension();

  public static XmlExtension getExtension(XmlFile file) {
    for (XmlExtension extension : Extensions.getExtensions(EP_NAME)) {
      if (extension.isAvailable(file)) {
        return extension;
      }
    }
    return DEFAULT_EXTENSION;
  }

  public abstract boolean isAvailable(XmlFile file);

  @NotNull
  public abstract Set<String> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context);
  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context);

  public static interface Runner<P, T extends Throwable> {
    void run(P param) throws T;
  }

  public abstract void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                                    @NotNull final Editor editor, 
                                                    @NotNull final Set<String> possibleNamespaces,
                                                    @Nullable final String nsPrefix,
                                                    @Nullable Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException;

  public String getNamespaceAlias() {
    return XmlBundle.message("namespace.alias");
  }
}
