package org.jetbrains.plugins.textmate.plist;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.textmate.plist.PListValue.value;

public class XmlPlistReader implements PlistReader {
  @Override
  public Plist read(@NotNull InputStream inputStream) throws IOException {
    return internalRead(XmlDomReader.readXmlAsModel(inputStream));
  }

  @Override
  public Plist read(@NotNull File file) throws IOException {
    try (FileInputStream fs = new FileInputStream(file)) {
      return read(fs);
    }
  }

  private static Plist internalRead(@NotNull XmlElement root) throws IOException {
    if (root.children.isEmpty()) {
      return Plist.EMPTY_PLIST;
    }

    if (!"plist".equals(root.name)) {
      throw new IOException("Unknown xml format. Root element is '" + root.name + "'");
    }

    XmlElement dictElement = root.getChild("dict");
    return dictElement != null ? (Plist)readDict(dictElement).getValue() : Plist.EMPTY_PLIST;
  }

  private static PListValue readDict(@NotNull XmlElement dictElement) throws IOException {
    Plist dict = new Plist();
    List<XmlElement> children = dictElement.children;
    int i = 0;
    for (; i < children.size(); i++) {
      XmlElement keyElement = children.get(i);
      if ("key".equals(keyElement.name)) {
        String attributeKey = keyElement.content;
        i++;
        PListValue value = attributeKey != null ? readValue(attributeKey, children.get(i)) : null;
        if (value != null) {
          dict.setEntry(attributeKey, value);
        }
      }
    }

    return value(dict, PlistValueType.DICT);
  }

  @Nullable
  private static PListValue readValue(@NotNull String key, @NotNull XmlElement valueElement) throws IOException {
    String type = valueElement.name;
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

  private static PListValue readArray(String key, XmlElement element) throws IOException {
    List<Object> result = new ArrayList<>();
    for (XmlElement child : element.children) {
      Object val = readValue(key, child);
      if (val != null) {
        result.add(val);
      }
    }
    return value(result, PlistValueType.ARRAY);
  }

  @Nullable
  private static PListValue readBasicValue(@NotNull String type, @NotNull XmlElement valueElement) throws IOException {
    String content = valueElement.content;

    if ("string".equals(type) && content != null) {
      return value(Strings.unescapeXmlEntities(content), PlistValueType.STRING);
    }
    else if ("true".equals(type)) {
      return value(Boolean.TRUE, PlistValueType.BOOLEAN);
    }
    else if ("false".equals(type)) {
      return value(Boolean.FALSE, PlistValueType.BOOLEAN);
    }
    else if ("integer".equals(type) && content != null) {
      return value(Long.parseLong(content), PlistValueType.INTEGER);
    }
    else if ("real".equals(type) && content != null) {
      return value(Double.parseDouble(content), PlistValueType.REAL);
    }
    else if ("date".equals(type) && content != null) {
      try {
        return value(Plist.dateFormatter().parse(content), PlistValueType.DATE);
      }
      catch (ParseException e) {
        throw new IOException(e);
      }
    }
    return null;
  }
}
