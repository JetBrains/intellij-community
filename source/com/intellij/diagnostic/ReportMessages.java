/*
 * @author: Eugene Zhuravlev
 * Date: Mar 18, 2003
 * Time: 1:25:33 PM
 */
package com.intellij.diagnostic;

public class ReportMessages {

  public static final boolean isEAP = true;
  public static final String ERROR_REPORT = "Error Report";

  public static String getReportAddress() {
    if (isEAP) {
      return "Please submit bug report to http://www.intellij.net/tracker/idea/browse";
    }
    return "For assistance, contact support@intellij.com";
  }
}
