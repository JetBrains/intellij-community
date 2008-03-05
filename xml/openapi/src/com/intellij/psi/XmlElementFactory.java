package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlElementFactory {

  public static XmlElementFactory getInstance(Project project) {
    return ServiceManager.getService(project, XmlElementFactory.class);
  }
  /**
   * Creates an XML text element from the specified string, escaping the special
   * characters in the string as necessary.
   *
   * @param s the text of the element to create.
   * @return the created element.
   * @throws com.intellij.util.IncorrectOperationException if the creation failed for some reason.
   */
  @NotNull
  public abstract XmlText createDisplayText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates an XHTML tag with the specified text.
   *
   * @param s the text of an XHTML tag (which can contain attributes and subtags).
   * @return the created tag instance.
   * @throws IncorrectOperationException if the text does not specify a valid XML fragment.
   */
  @NotNull
  public abstract XmlTag createXHTMLTagFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates an XML tag with the specified text.
   *
   * @param text the text of an XML tag (which can contain attributes and subtags).
   * @return the created tag instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid XML fragment.
   */
  @NotNull
  public abstract XmlTag createTagFromText(@NotNull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates an XML attribute with the specified name and value.
   *
   * @param name  the name of the attribute to create.
   * @param value the value of the attribute to create.
   * @return the created attribute instance.
   * @throws IncorrectOperationException if either <code>name</code> or <code>value</code> are not valid.
   */
  @NotNull
  public abstract XmlAttribute createXmlAttribute(@NotNull @NonNls String name, String value) throws IncorrectOperationException;

}
