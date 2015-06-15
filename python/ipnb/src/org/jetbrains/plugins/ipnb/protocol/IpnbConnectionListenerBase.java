package org.jetbrains.plugins.ipnb.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class IpnbConnectionListenerBase implements IpnbConnectionListener {
  @Override
  public void onOpen(@NotNull IpnbConnection connection) {
  }

  @Override
  public void onOutput(@NotNull IpnbConnection connection,
                       @NotNull String parentMessageId) {
  }

  public void onPayload(@Nullable final String payload,
                        @NotNull String parentMessageId) {
  }
}
