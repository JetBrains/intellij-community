import re
import os
import pip
import pytest


@pytest.fixture(scope='module')
def requirements(request):
    requirements_path = re.sub(r'(.*)_test\.py', r'\1_requirements.txt',
                               request.module.__file__)
    if os.path.exists(requirements_path):
        pip.main(['install', '-r', requirements_path])
    yield
    # We could uninstall everything here after the module tests finish
