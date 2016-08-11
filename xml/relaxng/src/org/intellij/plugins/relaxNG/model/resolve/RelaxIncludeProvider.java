package org.intellij.plugins.relaxNG.model.resolve;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.intellij.plugins.relaxNG.ApplicationLoader;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncInclude;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 09.06.2010
*/
public class RelaxIncludeProvider extends FileIncludeProvider {
  @NotNull
  @Override
  public String getId() {
    return "relax-ng";
  }

  @Override
  public boolean acceptFile(VirtualFile file) {
    final FileType type = file.getFileType();
    return type == XmlFileType.INSTANCE || type == RncFileType.getInstance();
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
    fileTypeSink.consume(RncFileType.getInstance());
  }

  @NotNull
  @Override
  public FileIncludeInfo[] getIncludeInfos(FileContent content) {
    final ArrayList<FileIncludeInfo> infos;

    if (content.getFileType() == XmlFileType.INSTANCE) {
      CharSequence inputDataContentAsText = content.getContentAsText();
      if (CharArrayUtil.indexOf(inputDataContentAsText, ApplicationLoader.RNG_NAMESPACE, 0) == -1) return FileIncludeInfo.EMPTY;
      infos = new ArrayList<>();
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(content.getContentAsText()), new RngBuilderAdapter(infos));
    } else if (content.getFileType() == RncFileType.getInstance()) {
      infos = new ArrayList<>();
      content.getPsiFile().acceptChildren(new RncElementVisitor() {
        @Override
        public void visitElement(RncElement element) {
          element.acceptChildren(this);
        }

        @Override
        public void visitInclude(RncInclude include) {
          final String path = include.getFileReference();
          if (path != null) {
            infos.add(new FileIncludeInfo(path));
          }
        }
      });
    } else {
      return FileIncludeInfo.EMPTY;
    }
    return infos.toArray(new FileIncludeInfo[infos.size()]);
  }

  private static class RngBuilderAdapter extends NanoXmlUtil.IXMLBuilderAdapter {
    boolean isRNG;
    boolean isInclude;
    private final ArrayList<FileIncludeInfo> myInfos;

    public RngBuilderAdapter(ArrayList<FileIncludeInfo> infos) {
      myInfos = infos;
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      boolean isRngTag = ApplicationLoader.RNG_NAMESPACE.equals(nsURI);
      if (!isRNG) { // analyzing start tag
        if (!isRngTag) {
          stop();
        } else {
          isRNG = true;
        }
      }
      isInclude = isRngTag && "include".equals(name);
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
      if (isInclude && "href".equals(key)) {
        myInfos.add(new FileIncludeInfo(value));
      }
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      isInclude = false;
    }
  }
}
