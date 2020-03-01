#!/bin/sh
git checkout backport_intellij_2019.3
git merge --no-edit origin/master
git push

git checkout backport_intellij_2019.2
git merge --no-edit origin/backport_intellij_2019.3
git push

git checkout backport_intellij_2019.1
git merge --no-edit origin/backport_intellij_2019.2
git push

git checkout backport_intellij_2017.2
git merge --no-edit origin/backport_intellij_2019.1
git push

git checkout backport_intellij_2017.1
git merge --no-edit origin/backport_intellij_2017.2
git push

git checkout backport_intellij_2016.2
git merge --no-edit origin/backport_intellij_2017.1
git push

