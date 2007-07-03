package org.jetbrains.idea.maven.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Vladislav.Kaznacheev
 */
public class Strings {
  public static List<String> tokenize(final String string, final String delim) {
    final List<String> tokens = new ArrayList<String>();
    for ( StringTokenizer tokenizer = new StringTokenizer(string, delim); tokenizer.hasMoreTokens();) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens;
  }

  public static String detokenize(final Collection<String> list, final char delim) {
    final StringBuffer stringBuffer = new StringBuffer();
    for ( String goal : list) {
      if(stringBuffer.length()!=0){
        stringBuffer.append(delim);
      }
      stringBuffer.append(goal);
    }
    return stringBuffer.toString();
  }
}
