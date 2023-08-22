import os
import re
import sys
import zipfile

from generator3.core import walk_python_path
from generator3.util_methods import is_text_file, say, report


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


def list_sources(paths):
    # noinspection PyBroadException
    try:
        for path in paths:
            path = os.path.normpath(path)

            if path.endswith('.egg') and os.path.isfile(path):
                say("%s\t%s\t%d", path, path, os.path.getsize(path))

            for root, files in walk_python_path(path):
                for name in files:
                    file_path = os.path.join(root, name)
                    if is_source_file(file_path):
                        say("%s\t%s\t%d", os.path.normpath(file_path), path, os.path.getsize(file_path))
        say('END')
        sys.stdout.flush()
    except:
        import traceback

        traceback.print_exc()
        sys.exit(1)


def zip_sources(zip_path):
    if not os.path.exists(zip_path):
        os.makedirs(zip_path)

    zip_filename = os.path.normpath(os.path.sep.join([zip_path, "skeletons.zip"]))

    try:
        zip = zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED)
    except:
        zip = zipfile.ZipFile(zip_filename, 'w')

    try:
        try:
            while True:
                line = sys.stdin.readline()

                if not line:
                    # TextIOWrapper.readline returns an empty string if EOF is hit immediately.
                    break

                line = line.strip()

                if line == '-':
                    break

                if line:
                    # This line will break the split:
                    # /.../dist-packages/setuptools/script template (dev).py setuptools/script template (dev).py
                    split_items = line.split()
                    if len(split_items) > 2:
                        # Currently it doesn't work for remote files like
                        # /System/Library/Frameworks/Python.framework/Versions/2.7/Extras/lib/python/setuptools/script (dev).tmpl
                        # TODO handle paths containing whitespaces more robustly
                        match_two_files = re.match(r'^(.+\.py)\s+(.+\.py)$', line)
                        if not match_two_files:
                            report("Error(zip_sources): invalid line '%s'" % line)
                            continue
                        split_items = match_two_files.group(1, 2)
                    (path, arcpath) = split_items

                    # An attempt to recursively pack an archive leads to unlimited explosion of its size
                    if os.path.samefile(path, zip_filename):
                        continue

                    zip.write(path, arcpath)
            say('OK: ' + zip_filename)
            sys.stdout.flush()
        except:
            import traceback

            traceback.print_exc()
            say('Error creating archive.')

            sys.exit(1)
    finally:
        zip.close()


def add_to_zip(zip, paths):
    # noinspection PyBroadException
    try:
        for path in paths:
            print("Walking root %s" % path)

            path = os.path.normpath(path)

            if path.endswith('.egg') and os.path.isfile(path):
                pass  # TODO: handle eggs

            for root, files in walk_python_path(path):
                for name in files:
                    file_path = os.path.join(root, name)
                    arcpath = os.path.relpath(file_path, path)

                    zip.write(file_path, os.path.join(str(hash(path)), arcpath))
    except:
        import traceback

        traceback.print_exc()
        sys.exit(1)


def zip_stdlib(roots, zip_path):
    if not os.path.exists(zip_path):
        os.makedirs(zip_path)

    import platform

    zip_filename = os.path.normpath(os.path.sep.join([zip_path, "%s-%s-stdlib-%s.zip" % (
        'Anaconda' if sys.version.find('Anaconda') != -1 else 'Python',
        '.'.join(map(str, sys.version_info)),
        platform.platform())]))

    print("Adding file to %s" % zip_filename)

    try:
        zip = zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED)
    except:
        zip = zipfile.ZipFile(zip_filename, 'w')

    try:
        add_to_zip(zip, roots)
    finally:
        zip.close()