package com.intellij.psi.impl.source.codeStyle.javadoc;

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

/**
 * Class comment
 *
 * @author Dmitry Skavish
 */
public class JDClassComment extends JDComment {
  public JDClassComment(CommentFormatter formatter) {
    super(formatter);
  }

  private ArrayList authorsList;
  private String version;

  protected void generateSpecial(String prefix, @NonNls StringBuffer sb) {
    if (!isNull(authorsList)) {
      for (Object aAuthorsList : authorsList) {
        String s = (String)aAuthorsList;
        sb.append(prefix);
        sb.append("@author ");
        sb.append(myFormatter.getParser().splitIntoCLines(s, prefix + "        ", false));
      }
    }
    if (!isNull(version)) {
      sb.append(prefix);
      sb.append("@version ");
      sb.append(myFormatter.getParser().splitIntoCLines(version, prefix + "         ", false));
    }
  }

  public void addAuthor(String author) {
    if (authorsList == null) {
      authorsList = new ArrayList();
    }
    authorsList.add(author);
  }

  public ArrayList getAuthorsList() {
    return authorsList;
  }

  public void setAuthorsList(ArrayList authorsList) {
    this.authorsList = authorsList;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}