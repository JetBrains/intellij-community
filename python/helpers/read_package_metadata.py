# -*- coding: utf-8 -*-
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#
# PEP 263 encoding declaration is required for the em-dashes in the comments below to parse
# under Python 2.7 — Python 3 defaults to UTF-8, but Py2 defaults to ASCII and rejects the
# file outright otherwise.
"""
Reads PEP 643 / Core Metadata for installed distributions in the current Python environment.

Used by [PythonPackageManager.getInstalledPackageMetadata] (see Kotlin side) to populate the
per-package metadata cache. Output is always a JSON object on stdout, mapping the canonical
(PEP 503-normalized) distribution name to a metadata record. Unknown / not-installed packages
are silently skipped — the caller decides how to handle a missing key.

Usage:
    python read_package_metadata.py            # all installed distributions
    python read_package_metadata.py NAME ...   # only the named distributions

The format intentionally mirrors the Core Metadata field names so the JSON keys map cleanly
onto the Kotlin `PythonPackageMetadata` data class via `kotlinx.serialization`.
"""
# `unicode_literals` keeps the pattern below and the literal string comparisons consistently
# typed across Python 2 and Python 3 — `email.message.Message` returns `unicode` on Py2, and
# mixing `str`/`unicode` in `re.sub` or `==` would emit warnings (or, with non-ASCII names,
# raise `UnicodeDecodeError`).
from __future__ import absolute_import, print_function, unicode_literals

import json
import re
import sys

try:
    from importlib import metadata as _md
except ImportError:  # py < 3.8 — only reachable on legacy interpreters
    try:
        import importlib_metadata as _md  # type: ignore[no-redef]
    except ImportError:
        # No backport installed on a legacy interpreter (typical for vanilla Python 2.7,
        # which doesn't ship `importlib_metadata`). Degrade gracefully: emit an empty map
        # and exit 0 so the caller's "no metadata yet" branch keeps working instead of the
        # whole helper exiting non-zero.
        json.dump({}, sys.stdout)
        sys.exit(0)


# https://peps.python.org/pep-0503/#normalized-names
_NORMALIZE_RE = re.compile(r"[-_.]+")


def _normalize_name(name):
    if not name:
        return ""
    return _NORMALIZE_RE.sub("-", name).lower()


def _coalesce_unknown(value):
    # `email.message.Message[key]` returns the literal string "UNKNOWN" for legacy metadata
    # entries that explicitly set the field to UNKNOWN; PEP 566 deprecates that, so treat it
    # as missing.
    if value is None or value == "UNKNOWN":
        return ""
    return value


def _parse_project_urls(entries):
    out = {}
    for entry in entries or ():
        if "," in entry:
            label, _, url = entry.partition(",")
            label = label.strip()
            url = url.strip()
            if label and url:
                out[label] = url
    return out


def _description(md):
    # Modern wheels store the long description in the message body (message.get_payload()).
    # Legacy distributions used a `Description:` header instead; fall back to that if the
    # body is empty so very old packages still surface a description.
    body = md.get_payload()
    if isinstance(body, list):
        # email.message returns a list when Content-Type is multipart — extremely rare for
        # METADATA, but be defensive.
        body = "\n".join(part.as_string() for part in body)
    body = body or ""
    if not body.strip():
        body = _coalesce_unknown(md.get("Description"))
    return body


def _to_dict(dist):
    md = dist.metadata
    return {
        "name": _coalesce_unknown(md.get("Name")),
        "version": _coalesce_unknown(md.get("Version")),
        "summary": _coalesce_unknown(md.get("Summary")),
        "description": _description(md),
        "description_content_type": _coalesce_unknown(md.get("Description-Content-Type")),
        "home_page": _coalesce_unknown(md.get("Home-page")),
        "author": _coalesce_unknown(md.get("Author")),
        "author_email": _coalesce_unknown(md.get("Author-email")),
        # PEP 639 introduced `License-Expression:` as the canonical license header; the legacy
        # `License:` field is deprecated but still common in older wheels. Take whichever is
        # populated so newer packages (numpy, …) still surface a license.
        "license": _coalesce_unknown(md.get("License") or md.get("License-Expression")),
        "requires_python": _coalesce_unknown(md.get("Requires-Python")),
        "keywords": _coalesce_unknown(md.get("Keywords")),
        "project_urls": _parse_project_urls(md.get_all("Project-URL")),
        "requires_dist": list(md.get_all("Requires-Dist") or ()),
        "classifiers": list(md.get_all("Classifier") or ()),
    }


def _safe_distribution(name):
    try:
        return _md.distribution(name)
    except _md.PackageNotFoundError:
        return None


def main():
    requested = sys.argv[1:]
    out = {}
    if requested:
        for raw in requested:
            dist = _safe_distribution(raw)
            if dist is None:
                continue
            key = _normalize_name(dist.metadata.get("Name") or raw)
            out[key] = _to_dict(dist)
    else:
        for dist in _md.distributions():
            name = dist.metadata.get("Name")
            if not name:
                continue
            out[_normalize_name(name)] = _to_dict(dist)
    json.dump(out, sys.stdout)


if __name__ == "__main__":
    main()
