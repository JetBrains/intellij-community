package com.intellij.errorreport.itn;

import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.ExceptionBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 4, 2003
 * Time: 8:12:00 PM
 * To change this template use Options | File Templates.
 */
public class ITNProxy {
  public static final String ENCODE = "UTF8";
  public static final String POST_DELIMETER = "&";

  public static final String NEW_THREAD_URL = "http://www.intellij.net/trackerRpc/idea/createScr";
  public static final String NEW_COMMENT_URL = "http://www.intellij.net/trackerRpc/idea/createComment";
  public static final String CHECK_THREAD_URL = "http://www.intellij.net/xml/scrToXml.jsp?projectName=idea&publicId=";
  public static final String BUILD_NUMBER_URL = "http://www.intellij.net/eap/products/idea/data/build_number.txt";

  public static final String THREAD_SUBJECT = "[{0}]";


  public static int getBuildNumber () throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL (BUILD_NUMBER_URL).openConnection();

    connection.setAllowUserInteraction(true);
    connection.connect();
    int responseCode = connection.getResponseCode();
    int buildNumber = -1;

    if (responseCode == HttpURLConnection.HTTP_OK) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      InputStream is = connection.getInputStream();

      int c;
      while ((c = is.read()) != -1) {
        baos.write (c);
      }

      try {
        buildNumber = Integer.valueOf(baos.toString().trim()).intValue();
      } catch (NumberFormatException ex) {
        // Tibor!!!! :-E
      }
    }

    connection.disconnect();

    switch (responseCode) {
      case HttpURLConnection.HTTP_OK:
        break;
      default:
        // some problems
        throw new IOException("Connection testing failed with HTTP code " + responseCode);
    }

    return buildNumber;
  }

  public static String getThreadStatus (int threadId) throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL (CHECK_THREAD_URL + threadId).openConnection();

    connection.setAllowUserInteraction(true);
    connection.connect();
    int responseCode = connection.getResponseCode();
    String threadStatus = "";

    if (responseCode == HttpURLConnection.HTTP_OK) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      InputStream is = connection.getInputStream();

      int c;
      while ((c = is.read()) != -1) {
        baos.write (c);
      }

      String result = baos.toString();
      int startIndex = result.indexOf("state=\"") + 7;
      int endIndex = result.indexOf("\"", startIndex);
      threadStatus = result.substring(startIndex, endIndex);
    }

    connection.disconnect();

    switch (responseCode) {
      case HttpURLConnection.HTTP_OK:
        break;
      default:
        // some problems
        throw new IOException("Connection testing failed with HTTP code " + responseCode);
    }

    return threadStatus;
  }


  private static HttpURLConnection post (String url, Map params) throws IOException, MalformedURLException {
    HttpURLConnection connection = (HttpURLConnection) new URL (url).openConnection();
    connection.setRequestMethod("POST");
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

    StringBuffer buffer = new StringBuffer();
    Iterator it = params.keySet().iterator();
    while (it.hasNext()) {
      String name = (String) it.next();
      if (params.containsKey(name) && params.get(name) != null)
        buffer.append(name + "=" + URLEncoder.encode((String) params.get(name), ENCODE) + POST_DELIMETER);
      else
        throw new IllegalArgumentException(name);
    }
    connection.setRequestProperty("Content-Length", Integer.toString(buffer.length()));
    connection.getOutputStream().write(buffer.toString().getBytes());
    return connection;
  }

  public static final String SUN = "Sun";
  public static final String JDK_1_2_2 = "1.2.2";
  public static final String JDK_1_3_0 = "1.3.0";
  public static final String JDK_1_3_1 = "1.3.1";
  public static final String JDK_1_3_1_01 = "1.3.1_01";
  public static final String JDK_1_4_0 = "1.4.0";
  public static final String JDK_1_4_0_01 = "1.4.0_01";
  public static final String JDK_1_4_0_02 = "1.4.0_02";
  public static final String JDK_1_4_1 = "1.4.1";
  public static final String JDK_1_4_2 = "1.4.2";

  public static final String WINDOWS_XP = "Windows XP";
  public static final String WINDOWS_2000 = "Windows 2000";
  public static final String WINDOWS_NT = "Windows NT";
  public static final String WINDOWS_95 = "Windows 95";
  public static final String WINDOWS_98 = "Windows 98";
  public static final String WINDOWS_ME = "Windows Me";
  public static final String SOLARIS = "Solaris";
  public static final String MAC_OS_X = "Mac Os X";
  public static final String LINUX = "Linux";

  public static int postNewThread (String userName, String password, ErrorBean error, ExceptionBean e,
                                   String compilationTimestamp)
          throws IOException, NoSuchEAPUserException, InternalEAPException {
    Map params = new HashMap ();
    params.put("username", userName);
    params.put("pwd", password);
    params.put("_title", MessageFormat.format(THREAD_SUBJECT,
            new Object [] {error.getLastAction() == null ? e.getExceptionClass() :
                           error.getLastAction() + ", " + e.getExceptionClass()}));
    ApplicationInfoEx appInfo =
      (ApplicationInfoEx) ApplicationManager.getApplication().getComponent(
        ApplicationInfo.class);

    String buildNumber = appInfo.getBuildNumber();
    try {
      buildNumber = Integer.valueOf(buildNumber).toString();
    } catch(NumberFormatException ex) {
      buildNumber = "";
    }

    params.put("_build", buildNumber);
    params.put("_description",
            (compilationTimestamp != null ? ("Build time: " + compilationTimestamp + "\n") : "") +
            error.getDescription() + "\n\n" + e.getStackTrace());
    params.put("addWatch", "true");

    String jdkVersion = System.getProperty("java.version");
    String jdkVendor = System.getProperty("java.vm.vendor");

    if (jdkVendor.indexOf(SUN) != -1) {
      if (jdkVersion.equals(JDK_1_4_2))
        jdkVersion = "10";
      else if (jdkVersion.equals(JDK_1_4_1))
        jdkVersion = "7";
      else if (jdkVersion.equals(JDK_1_4_0_02))
        jdkVersion = "9";
      else if (jdkVersion.equals(JDK_1_4_0_01))
        jdkVersion = "8";
      else if (jdkVersion.equals(JDK_1_4_0))
        jdkVersion = "6";
      else if (jdkVersion.equals(JDK_1_3_1_01))
        jdkVersion = "5";
      else if (jdkVersion.equals(JDK_1_3_1))
        jdkVersion = "4";
      else if (jdkVersion.equals(JDK_1_3_0))
        jdkVersion = "3";
      else if (jdkVersion.equals(JDK_1_2_2))
        jdkVersion = "2";
      else
        jdkVersion = "1";
    } else
      jdkVersion = "1";

    params.put("_jdk", jdkVersion);

    String os = error.getOs();
    if (os == null)
      os = "";

    if (os.indexOf(WINDOWS_XP) != -1)
      os = "4";
    else if (os.indexOf(WINDOWS_2000) != -1 || os.indexOf(WINDOWS_NT) != -1)
      os = "3";
    else if (os.indexOf(WINDOWS_95) != -1 || os.indexOf(WINDOWS_98) != -1 || os.indexOf(WINDOWS_ME) != -1)
      os = "2";
    else if (os.indexOf(SOLARIS) != -1)
      os = "7";
    else if (os.indexOf(MAC_OS_X) != -1)
      os = "6";
    else if (os.indexOf(LINUX) != -1)
      os = "5";
    else
      os = "1";
    params.put("_os", os);

    params.put("_visibility", "2"); // public
    params.put("command", "createSCR");
    params.put("_type", "1");  // bug

    HttpURLConnection connection = post(NEW_THREAD_URL, params);
    int responce = connection.getResponseCode();
    switch (responce) {
      case HttpURLConnection.HTTP_OK:
        break;
      case HttpURLConnection.HTTP_BAD_REQUEST:
      case HttpURLConnection.HTTP_NOT_FOUND:
        // user not found
        throw new NoSuchEAPUserException(userName);
      default:
        // some problems
        throw new InternalEAPException("HTTP Result code: " + responce);
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    InputStream is = connection.getInputStream();

    int c;
    while ((c = is.read()) != -1) {
      baos.write (c);
    }
    int threadId;

    try {
      threadId = Integer.valueOf(baos.toString().trim()).intValue();
    } catch (NumberFormatException ex) {
      // Tibor!!!! :-E
      throw new InternalEAPException("ITN returns wrong data");
    }

    return threadId;
  }

  public static void postNewComment (String userName, String password,
                                     int threadId, String comment)
          throws IOException, InternalEAPException, NoSuchEAPUserException {
    Map params = new HashMap ();
    params.put("username", userName);
    params.put("pwd", password);
    params.put("publicId", Integer.toString(threadId));
    params.put("body", comment);
    params.put("command", "Submit");

    HttpURLConnection connection = post(NEW_COMMENT_URL, params);
    int responce = connection.getResponseCode();
    switch (responce) {
      case HttpURLConnection.HTTP_OK:
        break;
      case HttpURLConnection.HTTP_BAD_REQUEST:
      case HttpURLConnection.HTTP_NOT_FOUND:
        // user not found
        throw new NoSuchEAPUserException(userName);
      default:
        // some problems
        throw new InternalEAPException("HTTP result code: " + responce);
    }
  }
}
