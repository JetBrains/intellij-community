from distutils.core import setup

tests_require = [
    'nose'
]

setup(name='foo',
      version=0.1,
      requires=[
          'Markdown',
      ],
      install_requires=[
          'pip',
          'Django==1.3.1',
      ],
      tests_require=tests_require)

