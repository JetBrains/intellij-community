#!/usr/bin/env python3

# Symlinks are bad on Windows, so we cannot use them in typeshed.
# This checks that certain files are duplicated exactly.

import os
import filecmp
from os import path

consistent_files = [
    {'stdlib/2/builtins.pyi', 'stdlib/2/__builtin__.pyi'},
    {'stdlib/2/SocketServer.pyi', 'stdlib/3/socketserver.pyi'},
    {'stdlib/2/os2emxpath.pyi', 'stdlib/2/posixpath.pyi', 'stdlib/2/ntpath.pyi', 'stdlib/2/macpath.pyi'},
    {'stdlib/3/ntpath.pyi', 'stdlib/3/posixpath.pyi', 'stdlib/3/macpath.pyi', 'stdlib/3/posixpath.pyi'},
    {'stdlib/3.4/enum.pyi', 'third_party/3/enum.pyi'},
]

def main():
    files = [path.join(root, file) for root, dir, files in os.walk('.') for file in files]
    no_symlink = 'You cannot use symlinks in typeshed, please copy the file to its link.'
    for file in files:
        if path.islink(file):
            raise ValueError(no_symlink.format(file))
    for file1, *others in consistent_files:
        f1 = path.join(os.getcwd(), file1)
        for file2 in others:
            f2 = path.join(os.getcwd(), file2)
            if not filecmp.cmp(f1, f2):
                raise ValueError('File {f1} does not match file {f2}. Please copy it to {f2}'.format(f1=file1, f2=file2))

if __name__ == '__main__':
    main()
