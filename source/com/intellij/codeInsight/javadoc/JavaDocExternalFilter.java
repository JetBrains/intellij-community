package com.intellij.codeInsight.javadoc;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.net.HttpConfigurable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: May 2, 2003
 * Time: 8:35:34 PM
 * To change this template use Options | File Templates.
 */

public class JavaDocExternalFilter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocExternalFilter");

  private Project myProject;
  private PsiManager myManager;

  private static Pattern ourHTMLsuffix = Pattern.compile("[.][hH][tT][mM][lL]?");
  private static Pattern ourParentFolderprefix = Pattern.compile("^[.][.]/");
  private static Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");
  private static Pattern ourHTMLFilesuffix = Pattern.compile("/[^/]*[.][hH][tT][mM][lL]?$");
  private static Pattern ourHREFselector = Pattern.compile("<A[ \\t\\n\\r\\f]+HREF=\"([^>]*)\"");
  private static Pattern ourAnnihilator = Pattern.compile("/[^/^.]*/[.][.]/");
  private static Pattern ourIMGselector = Pattern.compile("<IMG[ \\t\\n\\r\\f]+SRC=\"([^>]*)\"");

  private static abstract class RefConvertor {
    private Pattern mySelector;

    public RefConvertor(Pattern selector) {
      mySelector = selector;
    }

    protected abstract String convertReference(String root, String href);

    public String refFilter(String root, String read) {
      String toMatch = read.toUpperCase();
      String ready = "";
      int prev = 0;
      Matcher matcher = mySelector.matcher(toMatch);

      while (matcher.find()) {
        String before = read.substring(prev, matcher.start(1) - 1);     // Before reference
        String href = read.substring(matcher.start(1), matcher.end(1)); // The URL

        prev = matcher.end(1) + 1;
        ready += before + "\"" + convertReference(root, href) + "\"";
      }

      return read = ready + read.substring(prev, read.length());
    }
  }

  private RefConvertor myIMGConvertor = new RefConvertor(ourIMGselector) {
    protected String convertReference(String root, String href) {
      if (StringUtil.startsWithChar(href, '#')) {
        return "doc_element://" + root + href;
      }

      return ourHTMLFilesuffix.matcher(root).replaceAll("/") + href;
    }
  };

  private RefConvertor[] myReferenceConvertors = new RefConvertor[]{
    new RefConvertor(ourHREFselector) {
      protected String convertReference(String root, String href) {
        if (BrowserUtil.isAbsoluteURL(href)) {
          return href;
        }

        if (StringUtil.startsWithChar(href, '#')) {
          return "doc_element://" + root + href;
        }

        String nakedRoot = ourHTMLFilesuffix.matcher(root).replaceAll("/");

        String stripped = ourHTMLsuffix.matcher(href).replaceAll("");
        int len = stripped.length();

        do stripped = ourParentFolderprefix.matcher(stripped).replaceAll(""); while (len > (len = stripped.length()));

        final String elementRef = stripped.replaceAll("/", ".");
        final String classRef = ourAnchorsuffix.matcher(elementRef).replaceAll("");

        return
          (myManager.findClass(classRef) != null)
          ? "psi_element://" + elementRef
          : "doc_element://" + doAnnihilate(nakedRoot + href);
      }
    },

    myIMGConvertor
  };

  public JavaDocExternalFilter(Project project) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
  }

  private static String doAnnihilate(String path) {
    int len = path.length();

    do {
      path = ourAnnihilator.matcher(path).replaceAll("/");
    }
    while (len > (len = path.length()));

    return path;
  }

  private interface Waiter{
    void sayYes();

    boolean runMe();
  }

  public static boolean isJavaDocURL(String url) {
    final InputStream stream = getStreamByUrl(url);

    if (stream == null) {
      return false;
    }

    final Waiter waiter = new Waiter(){
      Boolean key = new Boolean(false);
      Object LOCK = new Object();

      public void sayYes(){
        key = new Boolean(true);
        synchronized (LOCK) {
          LOCK.notify();
        }
      }

      public boolean runMe(){
        try {
          synchronized (LOCK) {
            LOCK.wait(600);
          }
        }
        catch (InterruptedException e) {
          return false;
        }

        return key.booleanValue();
      }
    };

    new Thread(new Runnable() {
      public void run() {
        try {
          BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
          int lookUp = 6;

          while (lookUp > 0) {
            if (reader.readLine().indexOf("Generated by javadoc") != -1) {
              waiter.sayYes();
            }

            lookUp--;
          }

          reader.close();
        }
        catch (final Exception e) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showMessageDialog("Cannot fetch remote source: " + e,
                                         "IO Error",
                                         Messages.getErrorIcon());
            }
          });
        }
      }
    }).start();

    return waiter.runMe();
  }

  private String correctRefs(String root, String read) {
    String result = read;

    for (RefConvertor myReferenceConvertor : myReferenceConvertors) {
      result = myReferenceConvertor.refFilter(root, result);
    }

    return result;
  }

  public String filterInternalDocInfo(String text, String surl) {
    if (text == null) {
      return null;
    }

    text = JavaDocUtil.fixupText(text);

    if (surl == null) {
      return text;
    }

    String root = ourAnchorsuffix.matcher(surl).replaceAll("");

    return correctRefs(root, text);
  }


  private static InputStream getStreamByUrl(final String surl) {
    try {
      if (surl.startsWith("jar:")) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(BrowserUtil.getDocURL(surl));

        if (file == null) {
          return null;
        }

        return file.getInputStream();
      }

      URL url = BrowserUtil.getURL(surl);
      HttpConfigurable.getInstance().prepareURL(url.toString());
      return url.openStream();
    }
    catch (final IOException e) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog("Cannot fetch remote JavaDocs: " + e,
                                     "IO Error",
                                     Messages.getErrorIcon());
        }
      });
    }

    return null;
  }

  public String getExternalDocInfo(String surl) {
    if (surl == null) {
      return null;
    }

    Matcher anchorMatcher = ourAnchorsuffix.matcher(surl);
    String startSection = "<!-- ======== START OF CLASS DATA ======== -->";
    String endSection = "SUMMARY ======== -->";
    boolean isClassDoc = true;

    if (anchorMatcher.find()) {
      isClassDoc = false;
      startSection = "<A NAME=\"" + anchorMatcher.group(1).toUpperCase() + "\"";
      endSection = "<A NAME=";
    }

    final String root = ourAnchorsuffix.matcher(surl).replaceAll("");

    InputStream stream = getStreamByUrl(surl);

    if (stream == null) {
      return null;
    }

    BufferedReader buf = new BufferedReader(new InputStreamReader(stream));
    StringBuffer data = new StringBuffer();

    data.append("<HTML>\n");

    String read;

    try {
      while (((read = buf.readLine()) != null) && read.toUpperCase().indexOf(startSection) == -1) ;

      if (read == null) {
        return null;
      }

      data.append(read);

      if (isClassDoc) {
        boolean skip = false;

        while (((read = buf.readLine()) != null) && !read.toUpperCase().equals("<DL>")) {
          if (read.toUpperCase().indexOf("</H2>") != -1) { // read=class name in <H2>
            data.append("</H2>\n");
            skip = true;
          }
          else if (!skip) data.append(read); //correctRefs(root, read));
        }

        data.append("<DL>\n");

        StringBuffer classDetails = new StringBuffer();

        while (((read = buf.readLine()) != null) && !read.toUpperCase().equals("<HR>")) {
          classDetails.append(read); //correctRefs(root, read));
          classDetails.append("\n");
        }

        while (((read = buf.readLine()) != null) && !read.toUpperCase().equals("<P>")) {
          data.append(read); //correctRefs(root, read));
          data.append("\n");
        }

        data.append(classDetails);
        data.append("<P>\n");
      }

      while (((read = buf.readLine()) != null) && read.indexOf(endSection) == -1) {

        if (read.toUpperCase().indexOf("<HR>") == -1) {
          data.append(read); //correctRefs(root, read));
          data.append("\n");
        }
      }

      data.append("</HTML>\n");

      buf.close();
    }
    catch (final IOException e) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog("Cannot fetch remote JavaDocs: " + e,
                                     "IO Error",
                                     Messages.getErrorIcon());
        }
      });
    }

    String docText = correctRefs(root, data.toString());

    if (LOG.isDebugEnabled()) {
      LOG.debug("Filtered JavaDoc: " + docText + "\n");
    }

    return JavaDocUtil.fixupText(docText);
  }
}
