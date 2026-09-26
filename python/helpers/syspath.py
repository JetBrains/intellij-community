import os.path
import sys

_helpers_root = os.path.dirname(os.path.abspath(__file__))
_working_dir = os.getcwd()


def get_pep660_editable_roots_from_metadata():
    try:
        # importlib.metadata was added in 3.8
        from importlib.metadata import distributions, Distribution
    except ImportError:
        return []

    # We only reach here on Python 3.8+ (or environments providing importlib.metadata).
    # Safe to import these urllib submodules (since Python 3.0+) here.
    from urllib.parse import urlsplit
    from urllib.request import url2pathname

    def read_from_direct_url_json(dist):
        # type: (Distribution) -> str | None

        import json
        direct_url_file = dist._path / 'direct_url.json'
        if direct_url_file.exists():
            with direct_url_file.open() as f:
                data = json.load(f)
                if data.get('dir_info', {}).get('editable'):
                    return data.get('url', '')
        return None

    def file_url_to_path(url):
        # type: (str) -> str | None

        parts = urlsplit(url)
        if parts.scheme != "file":
            return None

        # For local file URLs, netloc is "" or "localhost"
        if parts.netloc not in ("", "localhost"):
            # Non-local authorities exist but usually aren't meaningful for local sys.path usage
            return None

        return url2pathname(parts.path)

    editable_roots = []
    for dist in distributions():
        url = None  # type: str | None
        try:
            # 'origin' was added in Python 3.13
            if dist.origin.dir_info.editable:
                url = dist.origin.url
        except AttributeError:
            url = read_from_direct_url_json(dist)

        if url:
            path = file_url_to_path(url)
            if path:
                editable_roots.append(path)

    return editable_roots


if __name__ == "__main__":
    for root in sys.path:
        # The current working dir is not automatically included but can be if PYTHONPATH
        # contains an empty entry.
        if root != _helpers_root and root != os.curdir and root != _working_dir:
            print(root)

    for editable_root in get_pep660_editable_roots_from_metadata():
        print(editable_root)
