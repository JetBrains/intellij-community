# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""The version and URL for coverage.py"""
# This file is exec'ed in setup.py, don't import anything!

# Same semantics as sys.version_info.
version_info = (6, 5, 0, "final", 0)


def _make_version(major, minor, micro, releaselevel, serial):
    """Create a readable version string from version_info tuple components."""
    assert releaselevel in ['alpha', 'beta', 'candidate', 'final']
    version = "%d.%d.%d" % (major, minor, micro)
    if releaselevel != 'final':
        short = {'alpha': 'a', 'beta': 'b', 'candidate': 'rc'}[releaselevel]
        version += f"{short}{serial}"
    return version


def _make_url(major, minor, micro, releaselevel, serial):
    """Make the URL people should start at for this version of coverage.py."""
    url = "https://coverage.readthedocs.io"
    if releaselevel != 'final':
        # For pre-releases, use a version-specific URL.
        url += "/en/" + _make_version(major, minor, micro, releaselevel, serial)
    return url


__version__ = _make_version(*version_info)
__url__ = _make_url(*version_info)
