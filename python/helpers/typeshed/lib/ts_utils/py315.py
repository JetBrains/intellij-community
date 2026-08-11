"""Helpers for Python 3.15 test infrastructure."""

# These stubs require runtime dependencies that do not install cleanly on Python 3.15 yet.
PY315_INCOMPATIBLE_RUNTIME_DEPENDENCIES = {
    # Depend on numpy, which does not provide Python 3.15 wheels yet.
    "JACK-Client",
    "geopandas",
    "hnswlib",
    "networkx",
    "pycocotools",
    "pyogrio",
    "resampy",
    "shapely",
    "tensorflow",
    # Depends on matplotlib, which depends on contourpy. contourpy does not
    # provide Python 3.15 wheels yet.
    "seaborn",
}
