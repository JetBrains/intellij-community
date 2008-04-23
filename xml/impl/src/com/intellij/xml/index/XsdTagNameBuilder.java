package com.intellij.xml.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class XsdTagNameBuilder extends NanoXmlUtil.IXMLBuilderAdapter {

  private final static Logger LOG = Logger.getInstance("#com.intellij.xml.index.XsdTagNameBuilder");

  @Nullable
  public static Collection<String> computeTagNames(final InputStream is) {
    try {
      final XsdTagNameBuilder builder = new XsdTagNameBuilder();
      NanoXmlUtil.parse(is, builder);
      return builder.myTagNames;
    }
    finally {
      try {
        if (is != null) {
          is.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private Collection<String> myTagNames = new ArrayList<String>();
  private boolean myElementStarted;

  public void startElement(@NonNls final String name, @NonNls final String nsPrefix, final String nsURI, final String systemID, final int lineNr)
      throws Exception {

    myElementStarted = nsPrefix != null && nsPrefix.equals("xsd") && name.equals("element");
  }

  public void addAttribute(@NonNls final String key, final String nsPrefix, final String nsURI, final String value, final String type)
      throws Exception {
    if (myElementStarted && key.equals("name")) {
      myTagNames.add(value);
      myElementStarted = false;
    }
  }
}
