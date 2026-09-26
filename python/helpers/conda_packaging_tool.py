import sys
import traceback
from collections import namedtuple

ERROR_WRONG_USAGE = 1
ERROR_EXCEPTION = 4

# Conda's package metadata is exposed via two very different shapes — a dict-like entry from the
# legacy ``get_index`` and a ``PackageRecord`` attribute object from conda 25's ``SubdirData``.
# Normalise both into a single record so the printing loop doesn't care which API produced them.
Pkg = namedtuple("Pkg", ["name", "version", "depends"])


def usage():
    sys.stderr.write('Usage: conda_packaging_tool.py listall | channels | versions PACKAGE\n')
    sys.stderr.flush()
    exit(ERROR_WRONG_USAGE)


def do_list_available_packages():
    import conda
    version = conda.__version__
    version_splitted = version.split(".")

    if len(version_splitted) < 2:
        sys.stderr.write("Conda version %s" % version)
        sys.stderr.flush()
        return

    major_version = int(version_splitted[0])
    minor_version = int(version_splitted[1])

    for pkg in _iter_packages(major_version, minor_version):
        sys.stdout.write("\t".join([pkg.name, pkg.version, ":".join(pkg.depends)]) + chr(10))
        sys.stdout.flush()


def _iter_packages(major_version, minor_version):
    """Yield [Pkg] entries from the current conda's package index.

    Picks the right import path based on the conda version because the index API has been
    renamed/moved several times. conda 25 removed ``get_index`` from ``conda.core.index`` and
    moved package metadata under ``conda.core.subdir_data``, which returns ``PackageRecord``
    objects (attribute access) instead of the legacy dict-like entries returned by ``get_index``.
    """
    if major_version >= 25:
        for pkg in _iter_packages_subdir_data():
            yield pkg
        return

    if major_version >= 22 or (major_version >= 4 and minor_version >= 4):
        init_context()
        from conda.core.index import get_index
        index = get_index()
    elif major_version == 4 and minor_version >= 2:
        from conda.api import get_index
        index = get_index()
    elif major_version == 4 and minor_version == 1:
        from conda.cli.main_search import get_index
        index = get_index()
    else:
        from conda.cli.main_search import common
        index = common.get_index_trap()

    for pkg in index.values():
        yield Pkg(name=pkg["name"], version=pkg["version"], depends=pkg["depends"])


def _iter_packages_subdir_data():
    """Iterate package records via ``SubdirData`` for conda 25+.

    The ``SubdirData.query_all`` signature changed across releases: conda 25 accepts a single
    positional ``MatchSpec`` and infers ``channels``/``subdirs`` from the current context, while
    conda 26 made ``channels`` and ``subdirs`` required keyword arguments (passing only the spec
    raises ``TypeError``). Try the modern explicit form first, then fall back to the older one.
    """
    context = init_context()
    from conda.core.subdir_data import SubdirData
    try:
        from conda.models.match_spec import MatchSpec
        spec = MatchSpec("*")
    except ImportError:
        spec = "*"

    channels = getattr(context, "channels", None) if context is not None else None
    subdirs = getattr(context, "subdirs", None) if context is not None else None

    records = None
    if channels is not None and subdirs is not None:
        try:
            records = SubdirData.query_all(spec, channels=channels, subdirs=subdirs)
        except TypeError:
            records = None

    if records is None:
        records = SubdirData.query_all(spec)

    for pkg in records:
        yield Pkg(name=pkg.name, version=pkg.version, depends=pkg.depends)


def do_list_channels():
    context = init_context()
    if context:
        channels = context.channels
    else:
        import conda.config as config
        if hasattr(config, "get_channel_urls"):
            channels = config.get_channel_urls()
        else:
            channels = config.context.channels
    sys.stdout.write('\n'.join(channels))
    sys.stdout.write('\n')
    sys.stdout.flush()


def fetch_versions(package):
    import json
    try:
        from distutils.version import LooseVersion
    except ImportError:
        if sys.version_info[:2] >= (3, 12):
            LooseVersion = lambda v: v.split('.')
        else:
            raise
    from conda.cli.python_api import run_command, Commands

    stdout, stderr, ret_code = run_command(Commands.SEARCH, package, '--json')
    if ret_code != 0:
        raise Exception(stderr)
    results = json.loads(stdout)
    results = results.get(package, [])
    all_versions = (r.get('version') for r in results)
    return sorted(set(v for v in all_versions if v), key=LooseVersion, reverse=True)


def do_list_versions(package):
    sys.stdout.write('\n'.join(fetch_versions(package)))
    sys.stderr.write('\n')
    sys.stdout.flush()


def init_context():
    try:
        from conda.base.context import context
    except ImportError:
        return None
    context.__init__()
    return context


def main():
    retcode = 0
    try:
        if len(sys.argv) < 2:
            usage()
        cmd = sys.argv[1]
        if cmd == 'listall':
            if len(sys.argv) != 2:
                usage()
                return
            do_list_available_packages()
        elif cmd == 'channels':
            if len(sys.argv) != 2:
                usage()
                return
            do_list_channels()
        elif cmd == 'versions':
            if len(sys.argv) != 3:
                usage()
                return
            do_list_versions(sys.argv[2])
        else:
            usage()
    except Exception:
        traceback.print_exc()
        exit(ERROR_EXCEPTION)
    exit(retcode)


if __name__ == '__main__':
    main()
