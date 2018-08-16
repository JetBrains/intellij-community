package org.jetbrains.plugins.ipnb.protocol;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class IpnbConnectionListenerBase implements IpnbConnectionListener {
  Ref<Boolean> connectionOpened = null;

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

  @Override
  public void onFinished(@NotNull IpnbConnection connection, @NotNull String parentMessageId) {
  }
}
