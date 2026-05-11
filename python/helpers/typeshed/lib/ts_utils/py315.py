"""Helpers for Python 3.15 test infrastructure."""

# These stubs require runtime dependencies that do not install cleanly on Python 3.15 yet.
PY315_INCOMPATIBLE_RUNTIME_DEPENDENCIES = {
    # Depend on numpy, which does not provide Python 3.15 wheels yet.
    "JACK-Client",
    "geopandas",
    "hnswlib",
    "networkx",
    "pycocotools",
    "resampy",
    "shapely",
    "tensorflow",
    # Depends on referencing, which depends on rpds-py. rpds-py currently uses
    # PyO3, which rejects Python 3.15.
    "jsonschema",
    # Depends on matplotlib, which depends on contourpy. contourpy does not
    # provide Python 3.15 wheels yet.
    "seaborn",
}
