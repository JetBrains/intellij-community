import sys
import os
import subprocess
import re
import pathlib

# this script calls sync before any given `uv run` calls
# the first argument is the path to uv
# the second argument is the path to the script
# the rest of the arguments should mirror all the arguments originally passed to uv
# for example, if the uv command is `uv run --no-project --script script.py`, then
# this script should be called in the following way:
# `uv_sync_proxy.py /path/to/uv script.py run --no-project --script script.py`

uv_path = sys.argv[1]
script_path = sys.argv[2]

should_sync_project = False
venv_path = None

try:
    sync_output = subprocess.check_output(
        [uv_path, "sync", "--script", script_path],
        stderr=subprocess.STDOUT
    ).decode("utf-8")

    result = re.search(r"environment at: (.*)\n", sync_output)

    if result is not None:
        venv_path = pathlib.Path(result.groups()[0])

        if not os.path.exists(str(venv_path)):
            venv_path = None

    print(sync_output)
except subprocess.CalledProcessError as e:
    str_output = e.output.decode("utf-8")
    if "does not contain a PEP 723 metadata tag" in str_output:
        should_sync_project = True
    print(str_output, file=sys.stderr)


sync_segment = "" if should_sync_project else "--no-sync "
active_segment = "" if venv_path is None else "--active "

if venv_path is not None:
    os.environ["VIRTUAL_ENV"] = str(venv_path)

command = uv_path + " run " + active_segment + sync_segment + " ".join(sys.argv[4:])
print()
print(command)
os.system(command)
