from distutils.core import setup

tests_require = [
    'mynose'
]

setup(name='foo',
      version=0.1,
      tests_require=tests_require,
      setup_requires=[
          'numpy'
      ])