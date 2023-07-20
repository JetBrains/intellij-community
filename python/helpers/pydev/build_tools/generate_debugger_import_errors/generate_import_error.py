#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import os

from generation_utils import create_temp_dir, get_builtin_modules_name, \
    create_temp_file, clear_temp_dir, write_result_to_file, debug_file

START_GEN_STRING = 'Start generation for %s'
GEN_FINISHED_STRING = 'Generation finished for %s'


def main():
    create_temp_dir()
    try:
        builtin_modules = get_builtin_modules_name()
        old_env = os.environ.copy()
        for module_name in builtin_modules:
            try:
                module_name += '.py'
                create_temp_file(module_name)
                print(START_GEN_STRING % module_name)
                debug_file(module_name)
            except Exception as e:
                print(e)
            finally:
                print(GEN_FINISHED_STRING % module_name)
                clear_temp_dir()
                os.environ = old_env
    except Exception as e:
        print(e)

    write_result_to_file()


if __name__ == '__main__':
    main()
