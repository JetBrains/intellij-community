from distutils.core import setup

requires = [
    'mynose'
]

setup(name='foo',
      version=0.1,
      install_requires=requires,
      tests_require=requires)
