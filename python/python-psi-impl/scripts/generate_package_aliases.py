#!/usr/bin/env -S uv run --script
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# /// script
# requires-python = ">=3.12"
# dependencies = ["requests>=2.32", "remotezip>=0.12", "packaging>=24"]
# ///

"""
Regenerates `resources/tools/packages`, the "importable top-level module -> PyPI project" table
that `PyPsiPackageUtil.moduleToPackageName` reads.

Which projects are read: the most downloaded PyPI projects (https://hugovk.github.io/top-pypi-packages/)
and the projects that the current table names, so a regeneration keeps the long tail. For each project the
script lists the files of a wheel of its latest release (with HTTP range requests for a large wheel) and takes
the top-level modules from the file paths, including the directories that a `.pth` file adds to `sys.path`.
A project without a wheel is read from its sdist: from `top_level.txt` when the sdist has one, or else from
the source layout.

How a project is chosen for a module that several projects ship:
- `PREFERRED_PROJECTS` decides first, and its choices are listed without the checks for capitalized names below.
- A stub bundle such as `boto3-stubs-full`, which ships hundreds of modules, does not count. A module that only
  bundles ship is not listed, so the IDE installs the project named like it.
- An owner ships the module's `__init__.py` or module file. An add-on such as `PyQtWebEngine`, or a binary
  part such as `PyQt6-Qt6`, only adds files. When the most downloaded project is not an owner, an owner that
  depends on it, is named after the module and has at least a `SAME_NAME_DOWNLOAD_RATIO`th of its downloads
  wins, as `aws-cdk-lib` over `aws-cdk.asset-awscli-v1`, or else an owner named like the module. In a namespace
  package such as `google`, neither exists, and the most downloaded project wins.
- Of the owners, a dependency named after another owner is its part (`mlflow-skinny` of `mlflow`) and does
  not count. The owner named like the module wins unless another owner has at least `SAME_NAME_DOWNLOAD_RATIO`
  times its downloads (`PyJWT` over `jwt`). Otherwise the most downloaded owner wins.
- A project named like the module whose files could not be read still wins over the chosen one unless that has at
  least `SAME_NAME_DOWNLOAD_RATIO` times its downloads, so that a repack like `dlib-bin` does not replace `dlib`.

Which modules are listed: only those whose project the IDE cannot derive from the name alone, that is, when the
normalized names differ, or when the module name has capitals, which the IDE trusts only when the table lists
them. A module is not listed when the chosen project is a part, named `<module>-…`, of a project named like the
module that depends on it and has at least a `SAME_NAME_DOWNLOAD_RATIO`th of its downloads, as `pydantic-ai-slim`
of `pydantic-ai`: the IDE then installs the project named like the module. An alias stub like `discord`, which
depends on `discord.py` with far fewer downloads, does not count.

Because the IDE offers to install and import a listed capitalized name for an unresolved reference, such a name
is listed only when it is unlikely to be a class: its project is among the most downloaded, the name is not a
class of the standard library, its project is named after it or is among the `TRUSTED_RANK` most downloaded,
and a single capitalized word, such as `Tree`, needs a project among the `TRUSTED_RANK` most downloaded too.

An entry of the current table stays when its project could not be read and no project that was read ships
the module.

Usage, from the root of the repository (in an intellij-community checkout, without `community/`):
    uv run community/python/python-psi-impl/scripts/generate_package_aliases.py

The listings are cached in `out/package-aliases-cache.json` of the repository, so a rerun only fetches the
projects that are missing from the cache. Pass `--refresh` to fetch everything again.
"""

import argparse
import ast
import io
import json
import re
import sys
import sysconfig
import tarfile
import threading
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path, PurePosixPath

import requests
from packaging.requirements import InvalidRequirement, Requirement
from remotezip import RemoteZip

SCRIPT_DIR = Path(__file__).resolve().parent
TABLE = SCRIPT_DIR.parent / "resources" / "tools" / "packages"
TOP_PACKAGES_URL = "https://hugovk.github.io/top-pypi-packages/top-pypi-packages.min.json"
USER_AGENT = "intellij-python-package-aliases-generator"
CACHE_VERSION = 4
WHOLE_DOWNLOAD_LIMIT = 2 * 1024 * 1024
SDIST_DOWNLOAD_LIMIT = 30 * 1024 * 1024
BUNDLE_MODULE_COUNT = 300
SAME_NAME_DOWNLOAD_RATIO = 10
TRUSTED_RANK = 2000

# Names that projects ship by accident next to their real modules, and that an unresolved import more likely means
# as a module of the user's own project. Compared case-insensitively.
NOT_MODULES = {
    "agent", "agents", "api", "app", "apps", "auth", "bench", "benches", "benchmark", "benchmarks", "bin", "book",
    "build", "ci", "cli", "client", "commands", "common", "conf", "config", "configs", "conftest", "context",
    "contrib", "core", "cron", "data", "db", "demo", "demos", "dev", "distribute_setup", "doc", "docs", "dummy",
    "empty", "errors", "ez_setup", "example", "exampleapp", "examples", "ext", "external", "fabfile", "gateway",
    "helloworld", "helpers", "img", "include", "integration", "interfaces", "internal", "legal", "lib", "licenses",
    "log", "logs", "main", "man", "manage", "metadata", "misc", "model", "models", "net", "node_modules",
    "notebooks", "noxfile", "options", "plot", "prefix", "private", "profiling", "providers", "resources", "run",
    "run_tests", "runner", "runtests", "rust", "samples", "scraper", "scripts", "sdk", "series", "server",
    "service", "services", "settings", "setup", "share", "src", "static", "store", "tasks", "templates", "test",
    "test_app", "testdata", "testing", "tests", "tools", "typings", "unittests", "util", "utils", "v2", "vendor",
    "venv", "version", "versioneer", "web", "website", "workflows",
}
NOT_MODULE_PATTERN = re.compile(r"_version|test_.*|tests_.*|.*_tests?", re.IGNORECASE)

# The project to install for a module, where the selection picks a variant that most users do not want, or misses
# the project because only an sdist too large to read publishes it.
PREFERRED_PROJECTS = {
    # The headless build has more downloads from servers and CI, but it lacks the GUI functions.
    "cv2": "opencv-python",
    # Each of the providers that outnumber the distribution in downloads adds one subpackage.
    "airflow": "apache-airflow",
    "pyspark": "pyspark",
    "official": "tf-models-official",
    "orbit": "tf-models-official",
    "tensorflow_models": "tf-models-official",
    # Recent setuptools wheels no longer ship it, which leaves only a fork as a candidate.
    "pkg_resources": "setuptools",
    # The Pro extension adds to the community package.
    "localstack": "localstack-core",
    # The ninja build tool also ships a `ninja` module, but code imports the web framework.
    "ninja": "django-ninja",
    # The project named like the module publishes only an sdist that rarely builds.
    "fasttext": "fasttext-wheel",
    # The projects named like these modules are unrelated libraries with fewer users.
    "memcache": "python-memcached",
    "whisper": "openai-whisper",
    "Xlib": "python-xlib",
    # The project named like the module needs a system libclang; `libclang` bundles it.
    "clang": "libclang",
    "soda": "soda-core",
}

_local = threading.local()


def session() -> requests.Session:
    if not hasattr(_local, "session"):
        _local.session = requests.Session()
        _local.session.headers["User-Agent"] = USER_AGENT
    return _local.session


def ide_normalize(name: str) -> str:
    """The normalization of `PyPackageName.normalizePackageName`, which the IDE applies to both sides."""
    name = name.strip()
    if not name.startswith("_"):
        name = name.replace("_", "-")
    return name.replace(".", "-").lower()


def pep503_normalize(name: str) -> str:
    return re.sub(r"[-_.]+", "-", name).lower()


def squashed(name: str) -> str:
    return re.sub(r"[-_.]+", "", name).lower()


def is_module_name(name: str) -> bool:
    return (
        name.isidentifier() and not name.startswith("__") and name.lower() not in NOT_MODULES
        and not NOT_MODULE_PATTERN.fullmatch(name)
    )


def is_python_file(name: str) -> bool:
    return name.endswith((".py", ".pyi", ".so", ".pyd"))


def module_of(path: str) -> tuple[str, bool] | None:
    """The top-level module of a path relative to an import root, and whether the path makes it a package or module."""
    first, _, rest = path.partition("/")
    if rest:
        return (first, rest in ("__init__.py", "__init__.pyi")) if is_python_file(rest) else None
    if first.endswith((".py", ".pyi")):
        return first.rsplit(".", 1)[0], True
    if first.endswith((".so", ".pyd")):
        return first.split(".")[0], True
    return None


def wheel_modules(names: list[str], read) -> tuple[list[str], list[str]]:
    """The top-level modules, and those of them that the wheel owns rather than only adds files to."""
    roots = [""]
    for name in names:
        if "/" not in name and name.endswith(".pth"):
            for line in read(name).splitlines():
                line = line.strip().replace("\\", "/")
                if line and not line.startswith(("#", "import ")):
                    roots.append(line.rstrip("/") + "/")
    modules, owned = set(), set()
    for name in names:
        parts = name.split("/")
        if parts[0].endswith(".data") and len(parts) > 2 and parts[1] in ("purelib", "platlib"):
            name = "/".join(parts[2:])
        elif parts[0].endswith((".data", ".dist-info")):
            continue
        root = max((r for r in roots if name.startswith(r)), key=len)
        found = module_of(name[len(root):])
        if found and is_module_name(found[0]) and found[0] + "/" not in roots:
            modules.add(found[0])
            if found[1]:
                owned.add(found[0])
    return sorted(modules), sorted(owned)


def sdist_modules(names: list[str], read) -> tuple[list[str], list[str]] | None:
    """`names` are the archive paths, `read` returns the text of one of them. None when the layout tells nothing."""
    paths = [PurePosixPath(n) for n in names if len(PurePosixPath(n).parts) > 1]
    # Each sdist holds one root directory, `<name>-<version>/`.
    for path in paths:
        relative = PurePosixPath(*path.parts[1:])
        if relative.name == "top_level.txt" and relative.parent.name.endswith(".egg-info") and len(relative.parts) <= 3:
            modules = sorted({line.strip() for line in read(str(path)).splitlines() if is_module_name(line.strip())})
            return (modules, modules) if modules else None
    modules = set()
    for path in paths:
        parts = path.parts[1:]
        if parts[0] == "src":
            parts = parts[1:]
        if len(parts) == 2 and parts[1] == "__init__.py":
            module = parts[0]
        elif len(parts) == 1 and parts[0].endswith(".py"):
            module = parts[0][:-3]
        else:
            continue
        if is_module_name(module):
            modules.add(module)
    return (sorted(modules), sorted(modules)) if modules else None


def requirements(info: dict) -> list[str]:
    names = set()
    for spec in info.get("requires_dist") or []:
        try:
            requirement = Requirement(spec)
        except InvalidRequirement:
            continue
        if requirement.marker is None or "extra" not in str(requirement.marker):
            names.add(pep503_normalize(requirement.name))
    return sorted(names)


def download(url: str) -> bytes:
    response = session().get(url, timeout=120)
    response.raise_for_status()
    return response.content


def read_wheel(wheel: dict) -> tuple[list[str], list[str]]:
    # RemoteZip asks for a fixed-size tail, which the server refuses for a smaller file; a small wheel is cheaper whole.
    if wheel["size"] <= WHOLE_DOWNLOAD_LIMIT:
        archive = zipfile.ZipFile(io.BytesIO(download(wheel["url"])))
        return wheel_modules(archive.namelist(), lambda n: archive.read(n).decode("utf-8", "replace"))
    with RemoteZip(wheel["url"], headers={"User-Agent": USER_AGENT}, timeout=60) as archive:
        return wheel_modules(archive.namelist(), lambda n: archive.read(n).decode("utf-8", "replace"))


def read_sdist(sdist: dict) -> tuple[list[str], list[str]] | None:
    if sdist["size"] > SDIST_DOWNLOAD_LIMIT:
        return None
    content = io.BytesIO(download(sdist["url"]))
    if sdist["filename"].endswith(".zip"):
        archive = zipfile.ZipFile(content)
        return sdist_modules(archive.namelist(), lambda n: archive.read(n).decode("utf-8", "replace"))
    with tarfile.open(fileobj=content) as archive:
        members = [m.name for m in archive.getmembers() if m.isfile()]
        return sdist_modules(members, lambda n: archive.extractfile(n).read().decode("utf-8", "replace"))


def fetch_project(project: str) -> dict:
    response = session().get(f"https://pypi.org/pypi/{project}/json", timeout=60)
    if response.status_code == 404:
        return {"version": CACHE_VERSION, "name": None, "modules": [], "owned": [], "requires": []}
    response.raise_for_status()
    info = response.json()
    files = [f for f in info["urls"] if not f.get("yanked")]
    wheels = [f for f in files if f["packagetype"] == "bdist_wheel"]
    sdists = [f for f in files if f["packagetype"] == "sdist" and f["filename"].endswith((".tar.gz", ".zip"))]
    found = None
    if wheels:
        found = read_wheel(min(wheels, key=lambda f: (0 if f["filename"].endswith("-none-any.whl") else 1, f["size"])))
    elif sdists:
        found = read_sdist(sdists[0])
    modules, owned = found if found else (None, [])
    return {
        "version": CACHE_VERSION, "name": info["info"]["name"], "modules": modules, "owned": owned,
        "requires": requirements(info["info"]),
    }


def load_top_projects(limit: int) -> list[tuple[str, int]]:
    response = session().get(TOP_PACKAGES_URL, timeout=60)
    response.raise_for_status()
    return [(row["project"], row["download_count"]) for row in response.json()["rows"][:limit]]


def load_table() -> dict[str, str]:
    return dict(line.split() for line in TABLE.read_text(encoding="utf-8").splitlines() if line.strip())


def stdlib_class_names() -> set[str]:
    names = set()
    for path in Path(sysconfig.get_paths()["stdlib"]).rglob("*.py"):
        if "site-packages" in path.parts or "test" in path.parts or "tests" in path.parts:
            continue
        try:
            tree = ast.parse(path.read_bytes())
        except (SyntaxError, ValueError):
            continue
        names.update(node.name for node in tree.body if isinstance(node, ast.ClassDef))
    return names


def repo_root() -> Path:
    return next(p for p in SCRIPT_DIR.parents if (p / ".git").exists())


def fetch_all(projects: list[str], cache_path: Path, jobs: int) -> dict[str, dict]:
    cache = json.loads(cache_path.read_text()) if cache_path.exists() else {}
    missing = [p for p in projects if cache.get(pep503_normalize(p), {}).get("version") != CACHE_VERSION]
    print(f"{len(projects)} projects, {len(missing)} to fetch", file=sys.stderr)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    failures = 0
    with ThreadPoolExecutor(jobs) as executor:
        futures = {executor.submit(fetch_project, p): p for p in missing}
        for done, future in enumerate(as_completed(futures), 1):
            project = futures[future]
            try:
                cache[pep503_normalize(project)] = future.result()
            except Exception as e:
                failures += 1
                print(f"  {project}: {type(e).__name__}: {e}", file=sys.stderr)
            if done % 500 == 0:
                cache_path.write_text(json.dumps(cache))
                print(f"  {done}/{len(missing)}", file=sys.stderr)
    cache_path.write_text(json.dumps(cache))
    print(f"{failures} failed; rerun to retry them", file=sys.stderr)
    return {key: entry for key, entry in cache.items() if entry.get("version") == CACHE_VERSION}


class Selector:
    def __init__(self, cache: dict[str, dict], downloads: dict[str, int]):
        self.cache = cache
        self.downloads = downloads

    def downloads_of(self, entry: dict) -> int:
        return self.downloads.get(pep503_normalize(entry["name"]), 0)

    def choose(self, module: str, candidates: list[dict]) -> str | None:
        """
        `candidates` are the cache entries of the projects that ship `module`, the most downloaded first.
        None when only stub bundles ship it: the project named like the module is then a better guess.
        """
        if module in PREFERRED_PROJECTS:
            return PREFERRED_PROJECTS[module]
        candidates = [c for c in candidates if len(c["modules"]) < BUNDLE_MODULE_COUNT]
        if not candidates:
            return None
        chosen = self.choose_among(module, candidates)
        # A repack such as `dlib-bin` must not win over the project named like the module only because that
        # project publishes an sdist that could not be read.
        same_name = self.cache.get(pep503_normalize(module))
        if same_name and same_name["name"] and same_name["modules"] is None and (
            self.downloads_of(same_name) * SAME_NAME_DOWNLOAD_RATIO >= self.downloads_of(chosen)
        ):
            return same_name["name"]
        return chosen["name"]

    def choose_among(self, module: str, candidates: list[dict]) -> dict:
        top = candidates[0]
        owners = [c for c in candidates if module in c["owned"]]
        same_name = [c for c in owners if ide_normalize(c["name"]) == ide_normalize(module)]
        if top not in owners:
            top_key = pep503_normalize(top["name"])
            wholes = [
                c for c in owners
                if top_key in c["requires"] and squashed(module) in squashed(c["name"])
                and self.downloads_of(c) * SAME_NAME_DOWNLOAD_RATIO >= self.downloads_of(top)
            ]
            owners = wholes or same_name
            if not owners:
                return top
        keys = {pep503_normalize(c["name"]) for c in owners}
        parts = {
            r for c in owners for r in c["requires"] if r in keys and r.startswith(pep503_normalize(c["name"]) + "-")
        }
        owners = [c for c in owners if pep503_normalize(c["name"]) not in parts] or owners
        same_name = [c for c in owners if ide_normalize(c["name"]) == ide_normalize(module)]
        same_name_downloads = max(self.downloads_of(same_name[0]), 1) if same_name else 0
        if same_name and self.downloads_of(owners[0]) < SAME_NAME_DOWNLOAD_RATIO * same_name_downloads:
            return same_name[0]
        return owners[0]

    def is_part_of_same_name(self, module: str, provider: str) -> bool:
        """
        Whether `provider` is a part, like `pydantic-ai-slim`, of a distribution named like the module. An alias
        stub such as `discord` for `discord.py` also depends on the provider, but has far fewer downloads.
        """
        same_name = self.cache.get(pep503_normalize(module))
        return bool(
            same_name and same_name["name"] and pep503_normalize(provider) in same_name["requires"]
            and ide_normalize(provider).startswith(ide_normalize(module) + "-")
            and self.downloads_of(same_name) * SAME_NAME_DOWNLOAD_RATIO
            >= self.downloads.get(pep503_normalize(provider), 0)
        )


def build_table(
    projects: list[str], downloads: dict[str, int], cache: dict[str, dict], current: dict[str, str],
) -> dict[str, str]:
    ranks = {pep503_normalize(p): i for i, p in enumerate(downloads)}
    class_names = stdlib_class_names()
    selector = Selector(cache, downloads)

    def listable(module: str, project: str) -> bool:
        if not is_module_name(module):
            return False
        if module == module.lower():
            return True
        rank = ranks.get(pep503_normalize(project), len(ranks))
        if rank >= len(ranks) or module in class_names:
            return False
        if squashed(module) not in squashed(project) and rank >= TRUSTED_RANK:
            return False
        return not re.fullmatch(r"[A-Z][a-z]+", module) or rank < TRUSTED_RANK

    candidates: dict[str, list[dict]] = {}
    seen = set()
    for project in projects:
        key = pep503_normalize(project)
        entry = cache.get(key)
        if key in seen or not entry or not entry["name"] or not entry["modules"]:
            continue
        seen.add(key)
        for module in entry["modules"]:
            candidates.setdefault(module, []).append(entry)

    table = {}
    for module, entries in candidates.items():
        provider = selector.choose(module, entries)
        if provider is None:
            continue
        derivable = module == module.lower() and ide_normalize(module) == ide_normalize(provider)
        if derivable:
            continue
        if module in PREFERRED_PROJECTS or (
            listable(module, provider) and not selector.is_part_of_same_name(module, provider)
        ):
            table[module] = provider

    for module, project in current.items():
        entry = cache.get(pep503_normalize(project))
        unreadable = entry is None or (entry["name"] is not None and entry["modules"] is None)
        if unreadable and module not in candidates and listable(module, project):
            table[module] = project
    return table


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--top", type=int, default=15000, help="how many of the most downloaded projects to read")
    parser.add_argument("--jobs", type=int, default=32, help="parallel requests")
    parser.add_argument("--cache", type=Path, default=None, help="default: out/package-aliases-cache.json")
    parser.add_argument("--refresh", action="store_true", help="ignore the cache")
    args = parser.parse_args()

    cache_path = args.cache or repo_root() / "out" / "package-aliases-cache.json"
    if args.refresh and cache_path.exists():
        cache_path.unlink()

    current = load_table()
    top = load_top_projects(args.top)
    downloads = {pep503_normalize(name): count for name, count in top}
    projects = list(dict.fromkeys([name for name, _ in top] + list(current.values())))
    cache = fetch_all(projects, cache_path, args.jobs)
    table = build_table(projects, downloads, cache, current)
    TABLE.write_text("".join(f"{module} {project}\n" for module, project in sorted(table.items())), encoding="utf-8")
    print(f"wrote {len(table)} entries to {TABLE}", file=sys.stderr)


if __name__ == "__main__":
    main()
