package com.intellij.xml.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class XIncludeProvider extends FileIncludeProvider {
  @NotNull
  @Override
  public String getId() {
    return "XInclude";
  }

  @Override
  public boolean acceptFile(VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileIncludeInfo[] getIncludeInfos(FileContent content) {

    final ArrayList<FileIncludeInfo> infos = new ArrayList<FileIncludeInfo>();
    NanoXmlUtil.parse(new ByteArrayInputStream(content.getContent()), new NanoXmlUtil.IXMLBuilderAdapter() {

      boolean isXInclude;
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
        isXInclude = XmlUtil.XINCLUDE_URI.equals(nsURI) && "include".equals(name);
      }

      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
        if (isXInclude && "href".equals(key)) {
          infos.add(new FileIncludeInfo(value));
        }
      }

      @Override
      public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        isXInclude = false;
      }
    });
    return infos.toArray(new FileIncludeInfo[infos.size()]);
  }
}
