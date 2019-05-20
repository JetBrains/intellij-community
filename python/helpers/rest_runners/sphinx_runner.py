
if __name__ == "__main__":
    import sys

    try:
        import sphinx
    except ImportError:
        raise NameError("Cannot find sphinx in selected interpreter.")

    version = sphinx.version_info
    if (version[0] >= 1 and version[1] >= 7) or version[0] >= 2:
        from sphinx.cmd import build
        build.main(sys.argv[1:])
    else:
        from sphinx import cmdline
        cmdline.main(sys.argv)
