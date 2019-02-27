import setuptools

try:
    with open('README.rst') as f:
        long_description = f.read()
except IOError:
    long_description = ""


setuptools.setup(
    name='stdlib-list',
    license='MIT',
    author='Jack Maney',
    author_email='jackmaney@gmail.com',
    url='https://github.com/jackmaney/python-stdlib-list',
    version=1.0.0,
    extras_require={"develop": ["sphinx"]},
    description='A list of Python Standard Libraries (2.6-7, 3.2-6).',
    long_description=long_description,
    include_package_data=True,
    packages=setuptools.find_packages(),
)
