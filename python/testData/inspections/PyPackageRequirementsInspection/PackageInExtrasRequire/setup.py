from distutils.core import setup

setup(name='foo',
      version=0.1,
      requires=['Markdown'],
      extras_require={"test": "pytest"})
