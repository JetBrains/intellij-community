package com.intellij.errorreport.itn;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.ExceptionBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 4, 2003
 * Time: 8:12:00 PM
 * To change this template use Options | File Templates.
 */
public class ITNProxy {
  @NonNls public static final String ENCODE = "UTF8";
  public static final String POST_DELIMETER = "&";

  @NonNls public static final String NEW_THREAD_URL = "http://www.intellij.net/trackerRpc/idea/createScr";
  @NonNls public static final String NEW_COMMENT_URL = "http://www.intellij.net/trackerRpc/idea/createComment";
  @NonNls public static final String CHECK_THREAD_URL = "http://www.intellij.net/xml/scrToXml.jsp?projectName=idea&publicId=";
  @NonNls public static final String BUILD_NUMBER_URL = "http://www.intellij.net/eap/products/idea/data/build_number.txt";

  public static final String THREAD_SUBJECT = "[{0}]";
  @NonNls private static final String HTTP_CONTENT_LENGTH = "Content-Length";
  @NonNls private static final String HTTP_CONTENT_TYPE = "Content-Type";
  @NonNls private static final String HTTP_WWW_FORM = "application/x-www-form-urlencoded";
  @NonNls private static final String HTTP_POST = "POST";


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
      //noinspection HardCodedStringLiteral
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
        throw new IOException(DiagnosticBundle.message("error.report.connection.failure", responseCode));
    }

    return threadStatus;
  }


  private static HttpURLConnection post (String url, Map<String,String> params) throws IOException, MalformedURLException {
    HttpURLConnection connection = (HttpURLConnection) new URL (url).openConnection();
    connection.setReadTimeout(10 * 1000);
    connection.setConnectTimeout(10 * 1000);
    connection.setRequestMethod(HTTP_POST);
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty(HTTP_CONTENT_TYPE, HTTP_WWW_FORM);

    StringBuffer buffer = new StringBuffer();
    for (String name : params.keySet()) {
      if (params.containsKey(name) && params.get(name) != null)
        buffer.append(name + "=" + URLEncoder.encode(params.get(name), ENCODE) + POST_DELIMETER);
      else
        throw new IllegalArgumentException(name);
    }
    connection.setRequestProperty(HTTP_CONTENT_LENGTH, Integer.toString(buffer.length()));
    connection.getOutputStream().write(buffer.toString().getBytes());
    return connection;
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public static final String SUN = "Sun";
  public static final String JDK_1_2_2 = "1.2.2";
  public static final String JDK_1_3_0 = "1.3.0";
  public static final String JDK_1_3_1 = "1.3.1";
  public static final String JDK_1_3_1_01 = "1.3.1_01";
  public static final String JDK_1_4_0 = "1.4.0";
  public static final String JDK_1_4_0_01 = "1.4.0_01";
  public static final String JDK_1_4_0_02 = "1.4.0_02";
  public static final String JDK_1_4_1 = "1.4.1";
  public static final String JDK_1_4_2 = "1.4.2";

  @NonNls public static final String WINDOWS_XP = "Windows XP";
  @NonNls public static final String WINDOWS_2000 = "Windows 2000";
  @NonNls public static final String WINDOWS_NT = "Windows NT";
  @NonNls public static final String WINDOWS_95 = "Windows 95";
  @NonNls public static final String WINDOWS_98 = "Windows 98";
  @NonNls public static final String WINDOWS_ME = "Windows Me";
  @NonNls public static final String SOLARIS = "Solaris";
  @NonNls public static final String MAC_OS_X = "Mac Os X";
  @NonNls public static final String LINUX = "Linux";

  public static int postNewThread (String userName, String password, ErrorBean error, ExceptionBean e,
                                   String compilationTimestamp)
          throws IOException, NoSuchEAPUserException, InternalEAPException {
    @NonNls Map<String,String> params = new HashMap<String, String>();
    params.put("username", userName);
    params.put("pwd", password);
    params.put("_title", MessageFormat.format(THREAD_SUBJECT,
                                              error.getLastAction() == null ? e.getExceptionClass() :
                                              error.getLastAction() + ", " + e.getExceptionClass()));
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

    String jdkVersion = SystemProperties.getJavaVersion();
    String jdkVendor = SystemProperties.getJavaVmVendor();

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
        throw new InternalEAPException(DiagnosticBundle.message("error.http.result.code", responce));
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
      throw new InternalEAPException(DiagnosticBundle.message("error.itn.returns.wrong.data"));
    }

    return threadId;
  }
}
