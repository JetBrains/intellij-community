package training.util;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.util.JdkBundle;
import com.intellij.util.JdkBundleList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;

/**
 * Created by jetbrains on 05/08/16.
 */
public class JdkSetupUtil {

    @NonNls
    private static final String productJdkConfigFileName =
            getExecutable() + (SystemInfo.isWindows ? ((SystemInfo.is64Bit) ? "64.exe.jdk" : ".exe.jdk") : ".jdk");
    @NonNls
    private static final File productJdkConfigFile = new File(PathManager.getConfigPath(), productJdkConfigFileName);
    @NonNls
    private static final File bundledJdkFile = getBundledJDKFile();


    @NotNull
    private static File getBundledJDKFile() {
        StringBuilder bundledJDKPath = new StringBuilder("jre");
        if (SystemInfo.isMac) {
            bundledJDKPath.append(File.separator).append("jdk");
        }
        return new File(bundledJDKPath.toString());
    }

    private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
    private static final String[] STANDARD_JVM_LOCATIONS_ON_LINUX = new String[]{
            "/usr/lib/jvm/", // Ubuntu
            "/usr/java/"     // Fedora
    };
    private static final String STANDARD_JVM_X64_LOCATIONS_ON_WINDOWS = "Program Files/Java";

    private static final String STANDARD_JVM_X86_LOCATIONS_ON_WINDOWS = "Program Files (x86)/Java";

    private static final Version JDK8_VERSION = new Version(1, 8, 0);

    @NotNull
    public static JdkBundleList findJdkPaths() {
        JdkBundle bootJdk = JdkBundle.createBoot();

        JdkBundleList jdkBundleList = new JdkBundleList();
        if (bootJdk != null) {
            jdkBundleList.addBundle(bootJdk, true);
        }

        if (new File(PathManager.getHomePath() + File.separator + bundledJdkFile).exists()) {
            JdkBundle bundledJdk = JdkBundle.createBundle(bundledJdkFile, false, true);
            if (bundledJdk != null) {
                jdkBundleList.addBundle(bundledJdk, true);
            }
        }

        if (SystemInfo.isMac) {
            jdkBundleList.addBundlesFromLocation(STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK8_VERSION, null);
        } else if (SystemInfo.isLinux) {
            for (String location : STANDARD_JVM_LOCATIONS_ON_LINUX) {
                jdkBundleList.addBundlesFromLocation(location, JDK8_VERSION, null);
            }
        } else if (SystemInfo.isWindows) {
            for (File root : File.listRoots()) {
                if (SystemInfo.is32Bit) {
                    jdkBundleList.addBundlesFromLocation(new File(root, STANDARD_JVM_X86_LOCATIONS_ON_WINDOWS).getAbsolutePath(), JDK8_VERSION, null);
                } else {
                    jdkBundleList.addBundlesFromLocation(new File(root, STANDARD_JVM_X64_LOCATIONS_ON_WINDOWS).getAbsolutePath(), JDK8_VERSION, null);
                }
            }
        }

        return jdkBundleList;
    }

    @NotNull
    private static String getExecutable() {
        final String executable = System.getProperty("idea.executable");
        return executable != null ? executable : ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    }

    public static String getJavaHomePath(JdkBundle jdkBundle){
        String homeSubPath = SystemInfo.isMac ? "/Contents/Home" : "";
        return jdkBundle.getLocation().getAbsolutePath() + homeSubPath;
    }
}

