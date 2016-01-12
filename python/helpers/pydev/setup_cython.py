'''
A simpler setup version just to compile the speedup module.

It should be used as:

python setup_cython build_ext --inplace
'''

import sys
target_pydevd_name = 'pydevd_cython'
for i, arg in enumerate(sys.argv[:]):
    if arg.startswith('--target-pyd-name='):
        del sys.argv[i]
        target_pydevd_name = arg[len('--target-pyd-name='):]




from setuptools import setup

import os
os.chdir(os.path.dirname(__file__))


pyx_file = os.path.join(os.path.dirname(__file__), "_pydevd_bundle", "pydevd_cython.pyx")
c_file = os.path.join(os.path.dirname(__file__), "_pydevd_bundle", "pydevd_cython.c")

if target_pydevd_name != 'pydevd_cython':
    import shutil
    from Cython.Build import cythonize # It MUST be there in this case! @UnusedImport
    new_pyx_file = os.path.join(os.path.dirname(__file__), "_pydevd_bundle", "%s.pyx" % (target_pydevd_name,))
    new_c_file = os.path.join(os.path.dirname(__file__), "_pydevd_bundle", "%s.c" % (target_pydevd_name,))
    shutil.copy(pyx_file, new_pyx_file)
    pyx_file = new_pyx_file
    assert os.path.exists(pyx_file)

try:
    try:
        from Cython.Build import cythonize
        # If we don't have the pyx nor cython, compile the .c
        if not os.path.exists(pyx_file):
            raise ImportError()
    except ImportError:
        from distutils.extension import Extension
        ext_modules = [Extension('_pydevd_bundle.%s' % (target_pydevd_name,), [
            "_pydevd_bundle/%s.c" % (target_pydevd_name,),
            ])]
    else:
        ext_modules = cythonize([
            "_pydevd_bundle/%s.pyx" % (target_pydevd_name,),
            ])

    setup(
            name='Cythonize',
            ext_modules=ext_modules
    )
finally:
    if target_pydevd_name != 'pydevd_cython':
        try:
            os.remove(new_pyx_file)
        except:
            import traceback
            traceback.print_exc()
        try:
            os.remove(new_c_file)
        except:
            import traceback
            traceback.print_exc()