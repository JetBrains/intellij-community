/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.filters.getters;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class HtmlAttributeValueGetter extends XmlAttributeValueGetter {
  private final boolean myCaseSensitive;

  public HtmlAttributeValueGetter(boolean _caseSensitive) {
    myCaseSensitive = _caseSensitive;
  }

  @Nullable
  @NonNls
  protected String[] addSpecificCompletions(final PsiElement context) {
    if (!(context instanceof XmlAttribute)) return null;

    XmlAttribute attribute = (XmlAttribute)context;
    @NonNls String name = attribute.getName();
    final XmlTag tag = attribute.getParent();

    @NonNls String tagName = tag != null ? tag.getName() : "";
    if (!myCaseSensitive) {
      name = name.toLowerCase();
      tagName = tagName.toLowerCase();
    }

    final String namespace = tag.getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {

      if ("target".equals(name)) {
        return new String[]{"_blank", "_top", "_self", "_parent"};
      }
      else if ("enctype".equals(name)) {
        return new String[]{"multipart/form-data", "application/x-www-form-urlencoded"};
      }
      else if ("rel".equals(name) || "rev".equals(name)) {
        return new String[]{"alternate", "stylesheet", "start", "next", "prev", "contents", "index", "glossary", "copyright", "chapter",
            "section", "subsection", "appendix", "help", "bookmark", "script"};
      }
      else if ("media".equals(name)) {
        return new String[]{"screen", "tty", "tv", "projection", "handheld", "print", "all", "aural", "braille"};
      }
      else if ("language".equals(name)) {
        return new String[]{"JavaScript", "VBScript", "JScript", "JavaScript1.2", "JavaScript1.3", "JavaScript1.4", "JavaScript1.5"};
      }
      else if ("type".equals(name) && "link".equals(tagName)) {
        return new String[]{"text/css", "text/html", "text/plain", "text/xml"};
      }
      else if ("http-equiv".equals(name) && "meta".equals(tagName)) {
        return new String[]{"Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language", "Accept-Ranges", "Age", "Allow",
            "Authorization", "Cache-Control", "Connection", "Content-Encoding", "Content-Language", "Content-Length", "Content-Location",
            "Content-MD5", "Content-Range", "Content-Type", "Date", "ETag", "Expect", "Expires", "From", "Host", "If-Match",
            "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Last-Modified", "Location", "Max-Forwards", "Pragma",
            "Proxy-Authenticate", "Proxy-Authorization", "Range", "Referer", "Retry-After", "Server", "TE", "Trailer", "Transfer-Encoding",
            "Upgrade", "User-Agent", "Vary", "Via", "Warning", "WWW-Authenticate"};
      }
      else if("content".equals(name) && "meta".equals(tagName)) {
        return new String[] {"application/activemessage", "application/andrew-inset", "application/applefile", "application/atomicmail", "application/dca-rft", "application/dec-dx", "application/mac-binhex40"
            , "application/mac-compactpro", "application/macwriteii", "application/msword", "application/news-message-id", "application/news-transmission", "application/octet-stream"
            , "application/oda", "application/pdf", "application/postscript", "application/powerpoint", "application/remote-printing", "application/rtf"
            , "application/slate", "application/wita", "application/wordperfect5.1", "application/x-bcpio", "application/x-cdlink", "application/x-compress"
            , "application/x-cpio", "application/x-csh", "application/x-director", "application/x-dvi", "application/x-gtar", "application/x-gzip"
            , "application/x-hdf", "application/x-httpd-cgi", "application/x-koan", "application/x-latex", "application/x-mif", "application/x-netcdf"
            , "application/x-sh", "application/x-shar", "application/x-stuffit", "application/x-sv4cpio", "application/x-sv4crc", "application/x-tar"
            , "application/x-tcl", "application/x-tex", "application/x-texinfo", "application/x-troff", "application/x-troff-man", "application/x-troff-me"
            , "application/x-troff-ms", "application/x-ustar", "application/x-wais-source", "application/zip", "audio/basic", "audio/mpeg"
            , "audio/x-aiff", "audio/x-pn-realaudio", "audio/x-pn-realaudio-plugin", "audio/x-realaudio", "audio/x-wav", "chemical/x-pdb"
            , "image/gif", "image/ief", "image/jpeg", "image/png", "image/tiff", "image/x-cmu-raster"
            , "image/x-portable-anymap", "image/x-portable-bitmap", "image/x-portable-graymap", "image/x-portable-pixmap", "image/x-rgb", "image/x-xbitmap"
            , "image/x-xpixmap", "image/x-xwindowdump", "message/external-body", "message/news", "message/partial", "message/rfc822"
            , "multipart/alternative", "multipart/appledouble", "multipart/digest", "multipart/mixed", "multipart/parallel", "text/html"
            , "text/plain", "text/richtext", "text/tab-separated-values", "text/x-setext", "text/x-sgml", "video/mpeg"
            , "video/quicktime", "video/x-msvideo", "video/x-sgi-movie", "x-conference/x-cooltalk", "x-world/x-vrml"};
      }
      else if("accept-charset".equals(name) || "charset".equals(name)) {
        Charset[] charSets = CharsetToolkit.getAvailableCharsets();
        String[] names = new String[charSets.length];
        for (int i = 0; i < names.length; i++) {
          names[i] = charSets[i].toString();
        }
        return names;
      }
    }

    return null;
  }
}
