# coding: utf-8
from __future__ import unicode_literals

import argparse
import json
import os
import sys
import zipfile
from collections import defaultdict

import six

_helpers_root = os.path.dirname(os.path.abspath(__file__))
_helpers_test_root = os.path.join(_helpers_root, 'tests')
_bytes_that_never_appears_in_text = (set(range(7))
                                     | {11}
                                     | set(range(14, 27))
                                     | set(range(28, 32))
                                     | {127})

if six.PY2:
    from io import open


    def dump_json(obj, path):
        with open(path, 'w', encoding='utf-8') as f:
            # json.dump cannot be safely used with ensure_ascii=False and io.open in Python 2
            # See http://bugs.python.org/issue13769
            f.write(unicode(json.dumps(obj,
                                       ensure_ascii=False,
                                       separators=(',', ':'),
                                       sort_keys=True)))

else:
    def dump_json(obj, path):
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(obj, f,
                      ensure_ascii=False,
                      separators=(',', ':'),
                      sort_keys=True)


# noinspection DuplicatedCode
def is_source_file(path):
    # Skip directories, character and block special devices, named pipes
    # Do not skip regular files and symbolic links to regular files
    if not os.path.isfile(path):
        return False

    # Want to see that files regardless of their encoding.
    if path.endswith(('-nspkg.pth', '.html', '.pxd', '.py', '.pyi', '.pyx')):
        return True
    has_bad_extension = path.endswith((
        # plotlywidget/static/index.js.map is 8.7 MiB.
        # Many map files from notebook are near 2 MiB.
        '.js.map',

        # uvloop/loop.c contains 6.4 MiB of code.
        # Some header files from tensorflow has size more than 1 MiB.
        '.h', '.c',

        # Test data of pycrypto, many files are near 1 MiB.
        '.rsp',

        # No need to read these files even if they are small.
        '.dll', '.pyc', '.pyd', '.pyo', '.so',
    ))
    if has_bad_extension:
        return False
    return is_text_file(path)


# noinspection DuplicatedCode
def is_text_file(path):
    """
    Verify that some path is a text file (not a binary file).
    Ideally there should be usage of libmagic but it can be not
    installed on a target machine.

    Actually this algorithm is inspired by function `file_encoding`
    from libmagic.
    """
    try:
        with open(path, 'rb') as candidate_stream:
            # Buffer size like in libmagic
            buffer = candidate_stream.read(256 * 1024)
    except EnvironmentError:
        return False

    # Verify that it looks like ASCII, UTF-8 or UTF-16.
    for encoding in 'utf-8', 'utf-16', 'utf-16-be', 'utf-16-le':
        try:
            buffer.decode(encoding)
        except UnicodeDecodeError as err:
            if err.args[0].endswith(('truncated data', 'unexpected end of data')):
                return True
        else:
            return True

    # Verify that it looks like ISO-8859 or non-ISO extended ASCII.
    return all(c not in _bytes_that_never_appears_in_text for c in buffer)


def path_is_under(path, parent):
    return not os.path.relpath(path, parent).startswith(os.pardir)


def open_zip(zip_path, mode):
    try:
        return zipfile.ZipFile(zip_path, mode, zipfile.ZIP_DEFLATED)
    except RuntimeError:
        return zipfile.ZipFile(zip_path, mode, zipfile.ZIP_STORED)


class RemoteSync(object):
    def __init__(self, roots, output_dir, state_json=None, project_roots=()):
        self.roots, self.skipped_roots = self.sanitize_roots(roots, project_roots)
        self.output_dir = self.sanitize_output_dir(output_dir)
        self.in_state_json = state_json
        self._name_counts = defaultdict(int)
        self._test_root = None

    def run(self):
        out_state_json = {'roots': []}
        for root in self.roots:
            zip_path = os.path.join(self.output_dir, self.root_zip_name(root))
            old_state = self.read_root_state(root)
            new_state = self.collect_sources_in_root(root, zip_path, old_state)
            out_state_json['roots'].append(new_state)

        if self.skipped_roots:
            out_state_json['skipped_roots'] = self.skipped_roots
        dump_json(out_state_json, os.path.join(self.output_dir, '.state.json'))

    def collect_sources_in_root(self, root, zip_path, old_state):
        new_state = self.empty_root_state()
        new_state['path'] = self.root_id(root)
        new_state['zip_name'] = os.path.basename(zip_path)

        old_entries = old_state['valid_entries']
        new_entries = new_state['valid_entries']
        with open_zip(zip_path, 'w') as zf:
            for path in self.find_sources_in_root(root):
                if os.path.samefile(path, zip_path):
                    continue
                rel_path = os.path.relpath(path, root)
                old_file_stat = old_entries.get(rel_path)
                cur_file_stat = self.file_stat(path)
                if not old_file_stat or self.is_modified(cur_file_stat, old_file_stat):
                    zf.write(path, rel_path)
                new_entries[rel_path] = cur_file_stat

        invalidated = list(six.viewkeys(old_entries) - six.viewkeys(new_entries))
        new_state['invalid_entries'] = sorted(invalidated)
        return new_state

    def find_sources_in_root(self, root):
        for root, dirs, files in os.walk(root):
            if root.endswith('__pycache__'):
                continue
            dirs_copy = list(dirs)
            for d in dirs_copy:
                dir_path = os.path.join(root, d)
                if d.endswith('__pycache__') or dir_path in self.roots:
                    dirs.remove(d)
            # some files show up but are actually non-existent symlinks
            for file in files:
                file_path = os.path.join(root, file)
                if is_source_file(file_path):
                    yield file_path

    def root_zip_name(self, root):
        root_name = os.path.basename(root)
        if root_name in self._name_counts:
            zip_name = '{}__{}.zip'.format(root_name, self._name_counts[root_name])
        else:
            zip_name = '{}.zip'.format(root_name)
        self._name_counts[root_name] += 1
        return zip_name

    @staticmethod
    def sanitize_path(path):
        return os.path.normpath(_decode_path(path))

    def sanitize_roots(self, roots, project_roots):
        result = []
        skipped_roots = []
        for root in roots:
            normalized = self.sanitize_path(root)
            if (not os.path.isdir(normalized) or
                    path_is_under(normalized, _helpers_root) and
                    not path_is_under(normalized, sys.prefix) and
                    not path_is_under(normalized, _helpers_test_root)):
                continue
            if any(path_is_under(normalized, p) for p in project_roots) \
                    and not path_is_under(normalized, sys.prefix):
                # Root is available locally and not under sys.prefix (hence not .venv)
                # Must be editable package on the target (for example, WSL or SSH)
                # Do not copy it, report instead
                skipped_roots.append(normalized)
                continue
            result.append(normalized)
        return result, skipped_roots

    def sanitize_output_dir(self, output_dir):
        normalized = self.sanitize_path(output_dir)
        for root in self.roots:
            if path_is_under(normalized, root):
                raise ValueError('Output directory {!r} cannot belong to root {!r}'
                                 .format(normalized, root))
        return normalized

    def read_root_state(self, root):
        if self.in_state_json:
            old_root_state = [r for r in self.in_state_json['roots']
                              if r['path'] == self.root_id(root)]
            if old_root_state:
                return old_root_state[0]
        return self.empty_root_state()

    @staticmethod
    def empty_root_state():
        return {
            'path': '',
            'zip_name': '',
            'valid_entries': {},
            'invalid_entries': [],
        }

    @staticmethod
    def file_stat(path):
        os_stat = os.stat(path)
        return {
            'mtime': int(os_stat.st_mtime),
        }

    @staticmethod
    def is_modified(cur_stat, old_stat):
        return cur_stat['mtime'] > old_stat['mtime']

    def root_id(self, path):
        if self._test_root:
            return os.path.relpath(path, self._test_root)
        return path


def _decode_cmd_arg(arg):
    if not isinstance(arg, bytes):
        return arg
    # Inspired by how Click handles command line arguments encoding
    # in 7.x Python 2 compatible version.
    stdin_enc = getattr(sys.stdin, "encoding", None)
    if stdin_enc:
        try:
            return arg.decode(stdin_enc)
        except UnicodeDecodeError:
            pass
    return _decode_path(arg)


def _decode_path(path):
    if not isinstance(path, bytes):
        return path
    fs_enc = sys.getfilesystemencoding() or sys.getdefaultencoding()
    try:
        return path.decode(fs_enc)
    except UnicodeDecodeError:
        pass
    return path.decode("utf-8", "replace")


class ArgparseTypes:
    @staticmethod
    def path(arg):
        return _decode_cmd_arg(arg)

    @staticmethod
    def path_list(arg):
        return [ArgparseTypes.path(p) for p in arg.split(os.pathsep)]


def main():
    parser = argparse.ArgumentParser(
        description='Collects sources in the given roots and packs them in individual '
                    'ZIP archives.'
    )
    parser.add_argument('output_dir', metavar='PATH', type=ArgparseTypes.path,
                        help='Directory to collect ZIP archives with sources into.')
    parser.add_argument('--state-file', type=argparse.FileType('rb'),
                        help='State of the last synchronization in JSON.')
    parser.add_argument('--project-roots', type=ArgparseTypes.path,
                        nargs='+', default=(),
                        help='Exclude roots from copying, report them to stdout instead')
    decoded_sys_path = [_decode_path(p) for p in sys.path]
    parser.add_argument('--roots', metavar='PATH_LIST', dest='roots',
                        type=ArgparseTypes.path_list, default=decoded_sys_path,
                        help='Roots to scan separated by `os.pathsep`, '
                             '`sys.path` by default.')
    args = parser.parse_args()

    state_file = args.state_file
    if not state_file:
        state_json = None
    elif state_file.name == '<stdin>':
        state_json = json.loads(state_file.readline())
    else:
        with args.state_file as f:
            # Python 3.5 cannot handle byte content passed to json.load()
            # even when encoding is specified
            state_json = json.loads(f.read().decode('utf-8'))

    RemoteSync(roots=args.roots,
               output_dir=args.output_dir,
               state_json=state_json,
               project_roots=set(args.project_roots)).run()


if __name__ == '__main__':
    main()
