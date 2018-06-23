@ECHO OFF

SET pluginVersion=0.18

for %%X in (2017.2 2017.3 2018.1 LATEST-EAP-SNAPSHOT) do call :buildPlugin %%X

:buildPlugin
SETLOCAL
echo Called with %1
SET IDEA_VERSION=%1
call gradlew clean
call gradlew buildPlugin check
copy build\distributions\lombok-plugin-%pluginVersion%.zip distro\lombok-plugin-%pluginVersion%-%1.zip
ENDLOCAL & SET result=%retval%
