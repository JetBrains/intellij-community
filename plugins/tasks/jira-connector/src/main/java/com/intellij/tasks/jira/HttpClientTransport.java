package com.intellij.tasks.jira;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClientException;
import org.apache.xmlrpc.XmlRpcTransport;

import java.io.IOException;
import java.io.InputStream;

public class HttpClientTransport implements XmlRpcTransport {

  private final String myUrl;
  private final HttpClient myHttpClient;
  private final Header myUserAgentHeader = new Header("User-Agent", XmlRpc.version);
  private PostMethod myMethod;

  public HttpClientTransport(String url, HttpClient httpClient) {
    myUrl = url;
    myHttpClient = httpClient;
  }

  public InputStream sendXmlRpc(byte[] request) throws IOException, XmlRpcClientException {
    myMethod = new PostMethod(myUrl);
    myMethod.setRequestHeader(new Header("Content-Type", "text/xml"));
    myMethod.setRequestHeader(myUserAgentHeader);
    myMethod.setRequestEntity(new ByteArrayRequestEntity(request));

    configureMethod(myMethod);

    myHttpClient.executeMethod(myMethod);
    return myMethod.getResponseBodyAsStream();
  }

  protected void configureMethod(HttpMethod method) {  
  }

  public void endClientRequest() throws XmlRpcClientException {
    myMethod.releaseConnection();
  }
}
