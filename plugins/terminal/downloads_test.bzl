"""Goldens of the URL shape of the libghostty-vt archive.

See `@community//build:downloads_test.bzl` for why these goldens exist.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":downloads.bzl", "lib_ghostty_vt_url")

def _lib_ghostty_vt_url_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        "https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/libghostty-vt/libghostty-vt-abc123.zip.zst",
        lib_ghostty_vt_url("abc123"),
    )
    return unittest.end(env)

lib_ghostty_vt_url_test = unittest.make(_lib_ghostty_vt_url_test_impl)

def downloads_test_suite(name):
    """The URL golden of the libghostty-vt download."""
    unittest.suite(
        name,
        lib_ghostty_vt_url_test,
    )
