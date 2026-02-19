from __future__ import unicode_literals

import os

from build import remove_binaries
from build_tools.build_common import regenerate_binaries, ensure_interpreters

miniconda64_envs = os.getenv('MINICONDA64_ENVS')
python_installations = [
    r'%s/py27_64/bin/python' % miniconda64_envs,
    r'%s/py36_64/bin/python' % miniconda64_envs,
    r'%s/py37_64/bin/python' % miniconda64_envs,
    r'%s/py38_64/bin/python' % miniconda64_envs,
    r'%s/py39_64/bin/python' % miniconda64_envs,
    r'%s/py310_64/bin/python' % miniconda64_envs,
    r'%s/py311_64/bin/python' % miniconda64_envs,
    r'%s/py312_64/bin/python' % miniconda64_envs,
    ]


def main():
    from generate_code import generate_dont_trace_files
    from generate_code import generate_cython_module

    # First, make sure that our code is up-to-date.
    generate_dont_trace_files()
    generate_cython_module()

    ensure_interpreters(python_installations)

    remove_binaries(['.so'])

    regenerate_binaries(python_installations)


if __name__ == '__main__':
    main()
