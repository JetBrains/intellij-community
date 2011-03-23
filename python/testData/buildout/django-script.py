#!"C:\Python26\python.exe"

import sys
sys.path[0:0] = [
    'c:\\src\\django\\buildout15\\src',
    'c:\\src\\django\\buildout15\\eggs\\djangorecipe-0.20-py2.6.egg',
    'c:\\src\\django\\buildout15\\eggs\\zc.recipe.egg-1.3.2-py2.6.egg',
    'c:\\src\\django\\buildout15\\eggs\\zc.buildout-1.5.2-py2.6.egg',
    'c:\\src\\django\\buildout15\\eggs\\setuptools-0.6c12dev_r88124-py2.6.egg',
    'c:\\src\\django\\buildout15\\parts\\django',
    'c:\\src\\django\\buildout15',
    ]


import djangorecipe.manage

if __name__ == '__main__':
    djangorecipe.manage.main('shorturls.testsettings')
