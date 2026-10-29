"""The pyrefly archives that the Python community plugin layout copies files out of. `pyreflyBuild` pins them."""

load("@community//build:download_helpers.bzl", "INTELLIJ_DEPENDENCIES_URL", "maven_url", "pinned", "read_properties")
load("@community//build:download_rules.bzl", "extracted_download_repo")
load("@community//build:host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")

_DEPENDENCIES = Label("@community//build:dependencies/dependencies.properties")

# OsFamily.osName
_PYREFLY_OS_NAMES = {
    "darwin": "macOS",
    "linux": "Linux",
    "windows": "Windows",
}

# JvmArchitecture.archName
_PYREFLY_ARCH_NAMES = {
    "aarch64": "AArch64",
    "x64": "X86_64",
}

def pyrefly_platform_directory(platform):
    """`pyreflyPlatformDirName`: the directory of the binary in the archive and in the plugin, such as `macOS-AArch64`."""
    parts = platform_parts(platform)
    return "%s-%s" % (_PYREFLY_OS_NAMES[parts.os], _PYREFLY_ARCH_NAMES[parts.arch])

# downloadPyrefly - `platform` is a [HOST_PLATFORMS] entry, or `None` for the license report archive
def pyrefly_url(platform, version):
    artifact = "pyrefly-" + (pyrefly_platform_directory(platform).lower() if platform else "license")
    return maven_url(INTELLIJ_DEPENDENCIES_URL, "org.jetbrains.intellij.deps", artifact, version, "tar.gz")

def _python_downloads_impl(module_ctx):
    # downloadPyrefly - one archive per platform holds `<os>-<arch>/pyrefly`, and one archive holds the `license` tree.
    # The plugin layout copies files of the unpacked archives, so each archive is an extracted repository alone.
    pyrefly_build = pinned(read_properties(module_ctx, _DEPENDENCIES), _DEPENDENCIES, "pyreflyBuild")
    extracted_download_repo(
        name = "pyrefly_license_extracted",
        url = pyrefly_url(None, pyrefly_build),
    )
    for platform in HOST_PLATFORMS:
        extracted_download_repo(
            name = "pyrefly_%s_extracted" % platform,
            url = pyrefly_url(platform, pyrefly_build),
        )
    return module_ctx.extension_metadata(reproducible = True)

python_downloads = module_extension(implementation = _python_downloads_impl)
