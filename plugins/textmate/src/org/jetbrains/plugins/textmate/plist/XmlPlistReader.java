package org.jetbrains.plugins.textmate.plist;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.textmate.plist.PListValue.value;

public class XmlPlistReader implements PlistReader {
  @Override
  public Plist read(@NotNull InputStream inputStream) throws IOException {
    try {
      return internalRead(JDOMUtil.load(inputStream));
    }
    catch (JDOMException e) {
      throw new IOException("Error while parsing plist", e);
    }
  }

  @Override
  public Plist read(@NotNull File file) throws IOException {
    try {
      return internalRead(JDOMUtil.load(FileUtil.loadFile(file)));
    }
    catch (JDOMException e) {
      throw new IOException("Error while parsing plist", e);
    }
  }

  private static Plist internalRead(@NotNull Element root) throws IOException {
    if (JDOMUtil.isEmpty(root)) {
      return Plist.EMPTY_PLIST;
    }

    if (!"plist".equals(root.getName())) {
      throw new IOException("Unknown xml format. Root element is '" + root.getName() + "'");
    }

    Element dictElement = root.getChild("dict");
    return dictElement != null ? (Plist)readDict(dictElement).getValue() : Plist.EMPTY_PLIST;
  }

  private static PListValue readDict(@NotNull Element dictElement) throws IOException {
    Plist dict = new Plist();
    List<Element> children = dictElement.getChildren();
    int i = 0;
    for (; i < children.size(); i++) {
      Element keyElement = children.get(i);
      if ("key".equals(keyElement.getName())) {
        String attributeKey = keyElement.getValue();
        i++;
        PListValue value = readValue(attributeKey, children.get(i));
        if (value != null) {
          dict.setEntry(attributeKey, value);
        }
      }
    }

    return value(dict, PlistValueType.DICT);
  }

  @Nullable
  private static PListValue readValue(@NotNull String key, @NotNull Element valueElement) throws IOException {
    String type = valueElement.getName();
    if ("dict".equals(type)) {
      return readDict(valueElement);
    }
    else if ("array".equals(type)) {
      return readArray(key, valueElement);
    }
    else {
      return readBasicValue(type, valueElement);
    }
  }

  private static PListValue readArray(String key, Element element) throws IOException {
    List<Object> result = new ArrayList<>();
    for (Element child : element.getChildren()) {
      Object val = readValue(key, child);
      if (val != null) {
        result.add(val);
      }
    }
    return value(result, PlistValueType.ARRAY);
  }

  @Nullable
  private static PListValue readBasicValue(@NotNull String type, @NotNull Element valueElement) throws IOException {
    if ("string".equals(type)) {
      return value(StringUtil.unescapeXmlEntities(valueElement.getValue()), PlistValueType.STRING);
    }
    else if ("true".equals(type)) {
      return value(Boolean.TRUE, PlistValueType.BOOLEAN);
    }
    else if ("false".equals(type)) {
      return value(Boolean.FALSE, PlistValueType.BOOLEAN);
    }
    else if ("integer".equals(type)) {
      return value(Long.parseLong(valueElement.getValue()), PlistValueType.INTEGER);
    }
    else if ("real".equals(type)) {
      return value(Double.parseDouble(valueElement.getValue()), PlistValueType.REAL);
    }
    else if ("date".equals(type)) {
      try {
        return value(Plist.dateFormatter().parse(valueElement.getValue()), PlistValueType.DATE);
      }
      catch (ParseException e) {
        throw new IOException(e);
      }
    }
    return null;
  }
}
