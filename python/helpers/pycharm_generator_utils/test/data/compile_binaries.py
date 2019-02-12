import os

from Cython.Build.Cythonize import main as cythonize

_test_data_dir = os.path.dirname(os.path.abspath(__file__))
_cythonize_options = ['--inplace']


def main():
    for dir_path, _, file_names in os.walk(_test_data_dir):
        for file_name in file_names:
            mod_name, ext = os.path.splitext(file_name)
            if ext == '.pyx':
                cythonize(_cythonize_options + [os.path.join(dir_path, file_name)])
                os.remove(os.path.join(dir_path, mod_name + '.c'))


if __name__ == '__main__':
    main()
