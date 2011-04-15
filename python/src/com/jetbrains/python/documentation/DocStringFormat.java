package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author yole
 */
public class DocStringFormat {
  public static final String PLAIN = "Plain";
  public static final String EPYTEXT = "Epytext";
  public static final String REST = "Restructuredtext";

  public static final List<String> ALL = ImmutableList.of(PLAIN, EPYTEXT, REST);

  private DocStringFormat() {
  }
}
