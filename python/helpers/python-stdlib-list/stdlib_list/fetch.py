import os
import warnings

from sphinx.ext.intersphinx import fetch_inventory

from . import base_dir, get_canonical_version, short_versions, list_dir


class DummyConfig(object):
    def __init__(self, intersphinx_mapping=None, intersphinx_cache_limit=5, intersphinx_timeout=None):
        self.intersphinx_mapping = intersphinx_mapping or {}
        self.intersphinx_cache_limit = intersphinx_cache_limit
        self.intersphinx_timeout = intersphinx_timeout
        self.tls_verify = True


class DummyApp(object):
    """
    Dummy app object for `fetch_inventory`_ to work.

    .. _fetch_inventory: https://github.com/sphinx-doc/sphinx/blob/64665f136b781426c526877283f30b690c1fa074/sphinx/ext/intersphinx.py#L125
    """

    def __init__(self):
        self.srcdir = base_dir
        self.warn = warnings.warn

        self.config = DummyConfig()

    def info(self, msg):
        print("DummyApp.info: {}".format(msg))


def fetch_list(version=None):
    """
    For the given version of Python (or all versions if no version is set), this function:

    - Uses the `fetch_inventory` function of :py:mod`sphinx.ext.intersphinx` to
    grab and parse the Sphinx object inventory
    (ie ``http://docs.python.org/<version>/objects.inv``) for the given version.

    - Grabs the names of all of the modules in the parsed inventory data.

    - Writes the sorted list of module names to file (within the `lists` subfolder).

    :param str|None version: A specified version of Python. If not specified, then all
    available versions of Python will have their inventory objects fetched
    and parsed, and have their module names written to file.
    (one of ``"2.6"``, ``"2.7"``, ``"3.2"``, ``"3.3"``, ``"3.4"``, ``"3.5"``, or ``None``)

    """

    if version is None:
        versions = short_versions
    else:
        versions = [get_canonical_version(version)]

    for version in versions:

        url = "http://docs.python.org/{}/objects.inv".format(version)

        modules = sorted(
            list(
                fetch_inventory(DummyApp(), "", url).get("py:module").keys()
            )
        )

        with open(os.path.join(list_dir, "{}.txt".format(version)), "w") as f:
            for module in modules:
                f.write(module)
                f.write("\n")
