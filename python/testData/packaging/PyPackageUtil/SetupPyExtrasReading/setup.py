from distutils.core import setup

tests_require = [
    'mynose'
]

setup(name='foo',
      version=0.1,
      requires=[
          'Markdown',
      ],
      install_requires='NewDjango==1.3.1',
      tests_require=tests_require,
      setup_requires=(
          'numpy',
      ),
      extras_require={"e1": "r1", "e2": ("r2",), "e3": ["r3", "r4"]})