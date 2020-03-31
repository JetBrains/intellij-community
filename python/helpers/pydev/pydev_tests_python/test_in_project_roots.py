def test_in_project_roots(tmpdir):
    from _pydevd_bundle import pydevd_utils
    import os.path
    import sys
    assert pydevd_utils._get_library_roots() == [
        os.path.normcase(x) for x in pydevd_utils._get_default_library_roots()]

    site_packages = tmpdir.mkdir('site-packages')
    project_dir = tmpdir.mkdir('project')

    project_dir_inside_site_packages = str(site_packages.mkdir('project'))
    site_packages_inside_project_dir = str(project_dir.mkdir('site-packages'))

    # Convert from pytest paths to str.
    site_packages = str(site_packages)
    project_dir = str(project_dir)
    tmpdir = str(tmpdir)

    # Test permutations of project dir inside site packages and vice-versa.
    pydevd_utils.set_project_roots([project_dir, project_dir_inside_site_packages])
    pydevd_utils.set_library_roots([site_packages, site_packages_inside_project_dir])

    check = [
        (tmpdir, False),
        (site_packages, False),
        (site_packages_inside_project_dir, False),
        (project_dir, True),
        (project_dir_inside_site_packages, True),
    ]
    for (check_path, find) in check[:]:
        check.append((os.path.join(check_path, 'a.py'), find))

    for check_path, find in check:
        assert pydevd_utils.in_project_roots(check_path) == find

    pydevd_utils.set_project_roots([])
    pydevd_utils.set_library_roots([site_packages, site_packages_inside_project_dir])

    # If the IDE did not set the project roots, consider anything not in the site
    # packages as being in a project root (i.e.: we can calculate default values for
    # site-packages but not for project roots).
    check = [
        (tmpdir, True),
        (site_packages, False),
        (site_packages_inside_project_dir, False),
        (project_dir, True),
        (project_dir_inside_site_packages, False),
        (os.path.join(tmpdir, '<foo>'), False),
    ]

    for check_path, find in check:
        assert pydevd_utils.in_project_roots(check_path) == find

    sys.path.append(str(site_packages))
    try:
        default_library_roots = pydevd_utils._get_default_library_roots()
        assert len(set(default_library_roots)) == len(default_library_roots), \
            'Duplicated library roots found in: %s' % (default_library_roots,)
    
        assert str(site_packages) in default_library_roots
        for path in sys.path:
            if os.path.exists(path) and path.endswith('site-packages'):
                assert path in default_library_roots
    finally:
        sys.path.remove(str(site_packages))
