package org.jetbrains.plugins.ipnb.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class IpnbParser {
    private static final Logger LOG = Logger.getInstance(IpnbParser.class);
    private static final Gson gson = initGson();

    @NotNull
    private static Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        return builder.create();
    }
    @NotNull
    public static IpnbCell parseIpnbCell(@NotNull String item)
            throws IOException {
        IpnbCell ipnbCell = gson.fromJson(item, IpnbCell.class);
        return ipnbCell;
    }
    @NotNull
    public static IpnbFile parseIpnbFile(@NotNull String fileText) throws IOException {
        IpnbFile ipnbFile = gson.fromJson(fileText, IpnbFile.class);
        return ipnbFile;
    }
}
