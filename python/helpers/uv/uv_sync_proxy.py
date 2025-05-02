import sys
import os
import subprocess

uv_path = sys.argv[1]
script_path = sys.argv[2]

should_sync_project = False

try:
    sync_output = subprocess.check_output(
        [uv_path, "sync", "--script", script_path],
        stderr=subprocess.STDOUT
    )
    print(sync_output.decode("utf-8"))
except subprocess.CalledProcessError as e:
    str_output = e.output.decode("utf-8")
    if "does not contain a PEP 723 metadata tag" in str_output:
        should_sync_project = True
    print(str_output, file=sys.stderr)


command = uv_path + " run " + ("" if should_sync_project else "--no-sync ") + " ".join(sys.argv[4:])
print()
print(command)
os.system(command)
