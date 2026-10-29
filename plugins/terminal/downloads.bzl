"""The libghostty-vt archive of the terminal plugin, one archive for every platform. `libGhosttyVtVersion` pins it."""

load("@community//build:download_helpers.bzl", "INTELLIJ_BUILD_DEPENDENCIES_URL", "pinned", "read_properties")
load("@community//build:download_rules.bzl", "download_repo")

_DEPENDENCIES = Label("@community//build:dependencies/dependencies.properties")

# TerminalLibGhosttyVtDownloader.downloadUrl
def lib_ghostty_vt_url(version):
    return "%s/libghostty-vt/libghostty-vt-%s.zip.zst" % (INTELLIJ_BUILD_DEPENDENCIES_URL, version)

def _terminal_downloads_impl(module_ctx):
    download_repo(
        name = "libghostty_vt",
        urls = [lib_ghostty_vt_url(pinned(read_properties(module_ctx, _DEPENDENCIES), _DEPENDENCIES, "libGhosttyVtVersion"))],
    )
    return module_ctx.extension_metadata(reproducible = True)

terminal_downloads = module_extension(implementation = _terminal_downloads_impl)
