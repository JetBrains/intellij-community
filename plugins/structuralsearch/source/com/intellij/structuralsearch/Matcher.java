package com.intellij.structuralsearch;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResultSink;

import java.util.*;

/**
 * This class makes program structure tree matching:
 */
public class Matcher extends MatcherImpl {

  public Matcher(Project _project) {
    super(_project);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public void findMatches(MatchResultSink sink,MatchOptions options) throws
    MalformedPatternException, UnsupportedPatternException
  {
    super.findMatches(sink,options);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param source string for search
   * @param pattern to be searched
   * @return list of matches found
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public List testFindMatches(String source,String pattern, MatchOptions options, boolean filePattern)
    throws MalformedPatternException, UnsupportedPatternException {

    return super.testFindMatches(source,pattern,options,filePattern);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param sink
   * @param options
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public void testFindMatches(MatchResultSink sink,MatchOptions options)
    throws MalformedPatternException, UnsupportedPatternException {

    super.testFindMatches(sink,options);
  }
}
