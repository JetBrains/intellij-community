package com.jetbrains.python.documentation;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.FP;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DocumentationBuilderKit {
  static final TagWrapper TagBold = new TagWrapper("b");
  static final TagWrapper TagItalic = new TagWrapper("i");
  static final TagWrapper TagSmall = new TagWrapper("small");
  static final TagWrapper TagCode = new TagWrapper("code");

  static final FP.Lambda1<String, String> LCombUp = new FP.Lambda1<String, String>() {
    public String apply(String argname) {
      return combUp(argname);
    }
  };
  final static @NonNls String BR = "<br>";
  static final FP.Lambda1<String, String> LSame1 = new FP.Lambda1<String, String>() {
    public String apply(String name) {
      return name;
    }
  };
  static final FP.Lambda1<Iterable<String>, Iterable<String>> LSame2 = new FP.Lambda1<Iterable<String>, Iterable<String>>() {
    public Iterable<String> apply(Iterable<String> what) {
      return what;
    }
  };
  public static FP.Lambda1<PyExpression, String> LReadableRepr = new FP.Lambda1<PyExpression, String>() {
    public String apply(PyExpression arg) {
      return PyUtil.getReadableRepr(arg, true);
    }
  };

  private DocumentationBuilderKit() {
  }

  static ChainIterable<String> wrapInTag(String tag, Iterable<String> content) {
    return new ChainIterable<String>("<" + tag + ">").add(content).add("</" + tag + ">");
  }

  @NonNls
  static String combUp(@NonNls String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }

  static ChainIterable<String> $(String... content) {
    return new ChainIterable<String>(Arrays.asList(content));
  }

  static <T> Iterable<T> interleave(Iterable<T> source, T filler) {
    List<T> ret = new LinkedList<T>();
    boolean is_next = false;
    for (T what : source) {
      if (is_next) ret.add(filler);
      else is_next = true;
      ret.add(what);
    }
    return ret;
  }

  // make a first-order curried objects out of wrapInTag()
  static class TagWrapper implements FP.Lambda1<Iterable<String>, Iterable<String>> {
    private final String myTag;

    TagWrapper(String tag) {
      myTag = tag;
    }

    public Iterable<String> apply(Iterable<String> contents) {
      return wrapInTag(myTag, contents);
    }

  }

  static class LinkWrapper implements FP.Lambda1<Iterable<String>, Iterable<String>> {
    private String myLink;

    LinkWrapper(String link) {
      myLink = link;
    }

    public Iterable<String> apply(Iterable<String> contents) {
      return new ChainIterable<String>()
        .add("<a href=\"").add(DocumentationManager.PSI_ELEMENT_PROTOCOL).add(myLink).add("\">")
        .add(contents).add("</a>")
      ;
    }
  }
}
