package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.xpath.XPathFileType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
@Tag("XPathResponseHandler")
public final class XPathResponseHandler extends SelectorBasedResponseHandler {
  private final Map<String, XPath> myCompiledCache = new HashMap<String, XPath>();

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public XPathResponseHandler() {
    // empty
  }

  public XPathResponseHandler(GenericRepository repository) {
    super(repository);
  }

  @NotNull
  @Override
  protected List<Object> selectTasksList(@NotNull String response, int max) throws Exception {
    Document document = new SAXBuilder(false).build(new StringReader(response));
    Element root = document.getRootElement();
    XPath xPath = lazyCompile(getSelector(TASKS).getPath());
    @SuppressWarnings("unchecked")
    List<Object> rawTaskElements = xPath.selectNodes(root);
    if (!rawTaskElements.isEmpty() && !(rawTaskElements.get(0) instanceof Element)) {
      throw new Exception(String.format("Expression '%s' should match list of XML elements. Got '%s' instead.",
                                        xPath.getXPath(), rawTaskElements.toString()));
    }
    return rawTaskElements.subList(0, Math.min(rawTaskElements.size(), max));
  }

  @Nullable
  @Override
  protected String selectString(@NotNull Selector selector, @NotNull Object context) throws Exception {
    if (StringUtil.isEmpty(selector.getPath())) {
      return null;
    }
    XPath xPath = lazyCompile(selector.getPath());
    String s = xPath.valueOf(context);
    if (s == null) {
      throw new Exception(String.format("XPath expression '%s' doesn't match", xPath.getXPath()));
    }
    return s;
  }

  @NotNull
  private XPath lazyCompile(@NotNull String path) throws Exception {
    if (!myCompiledCache.containsKey(path)) {
      try {
        final XPath compiled =  XPath.newInstance(path);
        myCompiledCache.put(path, compiled);
      }
      catch (JDOMException e) {
        throw new Exception(String.format("Malformed XPath expression '%s'", path));
      }
    }
    return myCompiledCache.get(path);
  }

  @Override
  public FileType getSelectorFileType() {
    return XPathFileType.XPATH;
  }

  @Override
  public ResponseType getResponseType() {
    return ResponseType.XML;
  }
}
