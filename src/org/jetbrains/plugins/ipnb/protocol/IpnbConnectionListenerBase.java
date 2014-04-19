package org.jetbrains.plugins.ipnb.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author vlan
 */
public class IpnbConnectionListenerBase implements IpnbConnectionListener {
  @Override
  public void onOpen(@NotNull IpnbConnection connection) {
  }

  @Override
  public void onOutput(@NotNull IpnbConnection connection, @NotNull String parentMessageId, @NotNull Map<String, String> outputs) {
  }
}
