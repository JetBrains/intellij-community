"""PY-92333: fetches the prebuilt pydevd_attach_to_process native binaries into this directory.

The binaries are not committed to git (see .gitignore here). They are built by the
"Debugpy.Attach binaries" TeamCity pipeline and published to a Space Files package repo; the
version to fetch is pinned in bin.debugpy-attach.properties, updated only by the
"Debugpy.Attach binaries: Prepare PR in Monorepo" bot job, never by hand.

Needs INTELLIJ_DEPENDENCIES_BOT and INTELLIJ_DEPENDENCIES_TOKEN in the environment (the same
credentials TeamCity uses for the shared intellij-dependencies Space Files repo).
"""

import base64
import io
import os
import tarfile
import urllib.request

_DIR = os.path.dirname(os.path.abspath(__file__))
_PROPERTIES_FILE = os.path.join(_DIR, "bin.debugpy-attach.properties")
_SPACE_FILES_REPO_URL = "https://packages.jetbrains.team/files/p/ij/intellij-dependencies"
_SPACE_NAME = "debugpy-attach-to-process"
_ARCHIVE_NAME = f"{_SPACE_NAME}.tar.gz"


def _read_pinned_commit_hash():
    with open(_PROPERTIES_FILE, encoding="utf-8") as f:
        for line in f:
            key, _, value = line.strip().partition("=")
            if key == "debugpyAttachToProcessCommitHash":
                return value
    raise RuntimeError(f"debugpyAttachToProcessCommitHash not found in {_PROPERTIES_FILE}")


def main():
    commit_hash = _read_pinned_commit_hash()
    url = f"{_SPACE_FILES_REPO_URL}/pydevd-native-deps/{_SPACE_NAME}/{commit_hash}/{_ARCHIVE_NAME}"

    username = os.environ["INTELLIJ_DEPENDENCIES_BOT"]
    token = os.environ["INTELLIJ_DEPENDENCIES_TOKEN"]
    credentials = base64.b64encode(f"{username}:{token}".encode()).decode()

    request = urllib.request.Request(url, headers={"Authorization": f"Basic {credentials}"})
    print(f"Downloading {url}")
    with urllib.request.urlopen(request) as response:
        archive_bytes = response.read()

    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:gz") as archive:
        archive.extractall(_DIR)

    print(f"Extracted {_ARCHIVE_NAME} (pinned commit {commit_hash}) into {_DIR}")


if __name__ == "__main__":
    main()
