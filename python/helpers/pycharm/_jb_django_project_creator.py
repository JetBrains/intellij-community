"""
Accepts 2 args: project name and dir where one should be created
"""
import sys, os
from django.core import management

project_name = sys.argv[1]
path = sys.argv[2]
if not os.path.exists(path):
    os.mkdir(path)

management.execute_from_command_line(argv=["django-admin", "startproject", project_name, path])