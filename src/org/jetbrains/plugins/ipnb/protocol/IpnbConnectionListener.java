package org.jetbrains.plugins.ipnb.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * TODO: Expose execution counter via API
 *
 * @author vlan
 */
public interface IpnbConnectionListener {
  void onOpen(@NotNull IpnbConnection connection);
  void onOutput(@NotNull IpnbConnection connection, @NotNull String parentMessageId, @NotNull Map<String, String> outputs);
}
