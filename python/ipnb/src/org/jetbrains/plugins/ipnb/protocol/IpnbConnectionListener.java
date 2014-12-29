package org.jetbrains.plugins.ipnb.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import java.util.List;

/**
 * TODO: Expose execution counter via API
 *
 * @author vlan
 */
public interface IpnbConnectionListener {
  void onOpen(@NotNull IpnbConnection connection);
  void onOutput(@NotNull IpnbConnection connection,
                @NotNull String parentMessageId,
                @NotNull List<IpnbOutputCell> outputs,
                @Nullable Integer execCount);
}
