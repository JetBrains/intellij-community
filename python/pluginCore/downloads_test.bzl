"""Goldens of the URL shape of the pyrefly archives.

See `@community//build:downloads_test.bzl` for why these goldens exist.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("@community//build:host_platforms.bzl", "HOST_PLATFORMS")
load(":downloads.bzl", "pyrefly_platform_directory", "pyrefly_url")

_DEPS = "https://cache-redirector.jetbrains.com/intellij-dependencies"

# downloadPyrefly, for `pyreflyBuild=263.6545`. The artifact is the lowercase `pyreflyPlatformDirName`.
_PYREFLY_URLS = {
    "darwin_aarch64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-macos-aarch64/263.6545/pyrefly-macos-aarch64-263.6545.tar.gz",
    "darwin_x64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-macos-x86_64/263.6545/pyrefly-macos-x86_64-263.6545.tar.gz",
    "linux_aarch64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-linux-aarch64/263.6545/pyrefly-linux-aarch64-263.6545.tar.gz",
    "linux_x64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-linux-x86_64/263.6545/pyrefly-linux-x86_64-263.6545.tar.gz",
    "windows_aarch64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-windows-aarch64/263.6545/pyrefly-windows-aarch64-263.6545.tar.gz",
    "windows_x64": _DEPS + "/org/jetbrains/intellij/deps/pyrefly-windows-x86_64/263.6545/pyrefly-windows-x86_64-263.6545.tar.gz",
}

# pyreflyPlatformDirName - the directory of the binary in the archive and in the plugin
_PYREFLY_DIRECTORIES = {
    "darwin_aarch64": "macOS-AArch64",
    "darwin_x64": "macOS-X86_64",
    "linux_aarch64": "Linux-AArch64",
    "linux_x64": "Linux-X86_64",
    "windows_aarch64": "Windows-AArch64",
    "windows_x64": "Windows-X86_64",
}

def _pyrefly_url_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_PYREFLY_URLS))
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_PYREFLY_DIRECTORIES))
    for platform, expected in _PYREFLY_URLS.items():
        asserts.equals(env, expected, pyrefly_url(platform, "263.6545"), platform)
        asserts.equals(env, _PYREFLY_DIRECTORIES[platform], pyrefly_platform_directory(platform), platform)
    asserts.equals(
        env,
        _DEPS + "/org/jetbrains/intellij/deps/pyrefly-license/263.6545/pyrefly-license-263.6545.tar.gz",
        pyrefly_url(None, "263.6545"),
    )
    return unittest.end(env)

pyrefly_url_test = unittest.make(_pyrefly_url_test_impl)

def downloads_test_suite(name):
    """The URL goldens of the pyrefly downloads."""
    unittest.suite(
        name,
        pyrefly_url_test,
    )
