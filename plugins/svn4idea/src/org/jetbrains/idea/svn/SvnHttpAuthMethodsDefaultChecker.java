package org.jetbrains.idea.svn;

public class SvnHttpAuthMethodsDefaultChecker {
  private static final String AUTH_METHODS_PROPERTY = "svnkit.http.methods";
  private static final String OLD_AUTH_METHODS_PROPERTY = "javasvn.http.methods";

  public static void check() {
    final String priorities = System.getProperty(AUTH_METHODS_PROPERTY, System.getProperty(OLD_AUTH_METHODS_PROPERTY));
    if (priorities == null) {
      System.setProperty(AUTH_METHODS_PROPERTY, "Basic,Digest,NTLM");
    }
  }
}
