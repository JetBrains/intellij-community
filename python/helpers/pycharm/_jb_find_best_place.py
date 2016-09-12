# First arg is project name. It searches for best place for project

import os, sys

project_name = sys.argv[1]
current_folder = os.getcwd()

while True:
    project_folder = os.path.join(current_folder, project_name)
    if not os.path.exists(project_name):
        print project_folder
        break
    else:
        project_name += "_"
