package org.jetbrains.plugins.ipnb.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Expose execution counter via API
 *
 * @author vlan
 */
public interface IpnbConnectionListener {
  void onOpen(@NotNull IpnbConnection connection);
  void onOutput(@NotNull IpnbConnection connection,
                @NotNull String parentMessageId);

  void onPayload(@Nullable final String payload,
                 @NotNull String parentMessageId);
}
