import sys
import sysconfig
from pathlib import Path


# On Windows you have lots of scripts (i.e `Scripts\pip.exe`). They are usually have hardcoded path to the python.
# It makes them non-transferable.
# This script replaces hard-coded paths to the one from the current interpreter.

# When download `.zip` file with pythons from buildserver, run this script on each python to make sure scripts are ok


def get_script_content(script_path: Path) -> bytes:
    with open(script_path, "r+b") as f:
        return f.read()


def put_script_data(script_path: Path, script_data: bytes):
    with open(script_path, "w+b") as f:
        return f.write(script_data)


def find_shebang_start_index(where_to_search_shebang: bytes) -> int:
    # Almost always shebang starts here (right after the last PE section)
    default_offset: int = 0x1A600
    if len(where_to_search_shebang) > default_offset and where_to_search_shebang[
        default_offset] == '#':
        return default_offset

    # Not found in default offset? Let's search
    for drive_letter_code in range(ord('a'), ord('z') + 1):
        for drive_letter in [chr(drive_letter_code), chr(drive_letter_code).upper()]:
            try:
                return where_to_search_shebang.index(
                    f"#!{drive_letter}:\\".encode('UTF-8'))
            except ValueError:
                pass


if __name__ == "__main__":
    new_shebang = f"#!{sys.executable}".encode('UTF-8')
    scripts_dir = sysconfig.get_path('scripts')
    for script in Path(scripts_dir).glob("*.exe"):
        script_content = get_script_content(script)
        shebang_start = find_shebang_start_index(script_content)
        if not shebang_start:
            print(f"No python found in {script}")
            continue
        shebang_end = script_content.find(b'\n', shebang_start)
        assert shebang_end, "No shebang end found: broken binary?"
        old_shebang = script_content[shebang_start:shebang_end]
        new_script_content = script_content.replace(old_shebang, new_shebang)
        put_script_data(script, new_script_content)
