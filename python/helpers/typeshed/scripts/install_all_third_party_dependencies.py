import subprocess
import sys

from ts_utils.requirements import get_external_stub_requirements


def main() -> None:
    requirements = get_external_stub_requirements()
    # By forwarding arguments, we naturally allow non-venv (system installs)
    # by letting the script's user follow uv's own helpful hint of passing the `--system` flag.
    subprocess.check_call(["uv", "pip", "install", *sys.argv[1:], *[str(requirement) for requirement in requirements]])


if __name__ == "__main__":
    main()
