from setuptools import setup

setup(name='foo',
      version=0.1,
      install_requires = ["sqlalchemy >=1.0.12, <1.1",
                          "mysql-connector-python >=2.1.3, <2.2",],
      dependency_links = [
           "git+https://github.com/mysql/mysql-connector-python.git@2.1.3#egg=mysql-connector-python-2.1.3",
      ])