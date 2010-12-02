package com.intellij.tasks.mantis;

import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.CommonsHTTPSender;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;

import java.net.URL;

/**
 * @author Dmitry Avdeev
 */
public class IdeaHttpSender extends CommonsHTTPSender {

  @Override
  protected HostConfiguration getHostConfiguration(HttpClient client, MessageContext context, URL targetURL) {
    HostConfiguration configuration = super.getHostConfiguration(client, context, targetURL);
    return configuration;
  }
}
