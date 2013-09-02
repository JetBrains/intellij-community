package org.jetbrains.plugins.ideaConfigurationServer;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.*;
import java.util.*;
import java.util.prefs.Preferences;


public class ClientUtil {
  private static final String hostName;
  private static final String userName;
  private static final String machineId;

  static {
    hostName = calcHostName();
    userName = System.getProperty("user.name", "<unknown>");
    String id = Preferences.userRoot().get("JetBrains.UserIdOnMachine", null);
    if (id == null || id.length() == 0) {
      id = UUID.randomUUID().toString();
      Preferences.userRoot().put("JetBrains.UserIdOnMachine", id);
    }
    machineId = id;
  }

  public static String getMachineId() {
    return machineId;
  }

  private static String calcHostName() {
    try {
      return getLocalHostName();
    }
    catch (Throwable ignored) {
      //OK
    }

    return "<unknown>";
  }

  public static String getHostName() {
    return hostName;
  }

  public static String getUserName() {
    return userName;
  }


  public static String detectServerUrl(final String serverName) {
    try {
      return parseDnsEntry(getLicenseServerDnsEntry(serverName)).get("url");
    }
    catch (Exception ignored) {
      return "http://jetbrains-idea-server";
    }
  }

  private static Collection<String> getLocalDomainSuffixes() {
    Set<String> domains = new HashSet<String>();
    final Enumeration<NetworkInterface> interfaces;
    try {
      interfaces = NetworkInterface.getNetworkInterfaces();
    }
    catch (SocketException ignored) {
      return domains;
    }

    while (interfaces.hasMoreElements()) {
      NetworkInterface networkInterface = interfaces.nextElement();
      final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (address.isMulticastAddress() || address instanceof Inet6Address || address.isLoopbackAddress()) {
          continue;
        }

        String domain = extractDomainSuffix(address.getCanonicalHostName());
        if (hasLetters(domain)) {
          domains.add(domain);
        }
      }
    }

    return domains;
  }

  private static boolean hasLetters(String domain) {
    for (int i = 0; i < domain.length(); i++) {
      if (Character.isLetter(domain.charAt(i))) return true;
    }
    return false;
  }

  private static String extractDomainSuffix(String nodeName) {
    final int suff = nodeName.indexOf('.');
    return suff > 0 ? nodeName.substring(suff + 1) : "";
  }

  private static String getLocalHostName() throws UnknownHostException {
    return InetAddress.getLocalHost().getCanonicalHostName();
  }

  private static String getLicenseServerDnsEntry(final String serverName) throws NamingException {
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

    final InitialDirContext context = new InitialDirContext(env);

    for (String domain : getLocalDomainSuffixes()) {
      try {
        final Attributes attributes = context.getAttributes(serverName + "" + domain);
        return (String)attributes.get("TXT").get();
      }
      catch (Exception ignored) {
        // Ignore
      }
    }

    throw new NamingException("No license server DNS entries detected in any scanned local domains");
  }

  private static Map<String, String> parseDnsEntry(String entry) {
    Map<String, String> map = new HashMap<String, String>();

    StringTokenizer tokenizer = new StringTokenizer(entry, ";", false);
    while (tokenizer.hasMoreTokens()) {
      String property = tokenizer.nextToken();
      int eq = property.indexOf('=');
      if (eq > 0) {
        String name = property.substring(0, eq).trim();
        String value = property.substring(eq + 1).trim();
        map.put(name, value);
      }
    }

    return map;
  }
}
