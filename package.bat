@echo off
rem Builds FAM and packages it with jpackage (JDK 17+ and Maven on PATH).
rem   package.bat           -> dist\FAM\FAM.exe  (portable app folder)
rem   package.bat installer -> dist\FAM-1.0.0.exe (needs WiX Toolset 3.x on PATH)
setlocal
call mvn -q clean package || exit /b 1
if exist dist rmdir /s /q dist
if exist target\jpackage-input rmdir /s /q target\jpackage-input
mkdir target\jpackage-input
copy target\FAM-app.jar target\jpackage-input\ >nul

set TYPE=app-image
set EXTRA=
if /i "%1"=="installer" (
  set TYPE=exe
  set EXTRA=--win-menu --win-shortcut --win-dir-chooser --win-per-user-install
)

jpackage --type %TYPE% --name FAM --app-version 1.0.0 ^
  --description "Link Line Embroidery FAM" --vendor "Link Line Embroidery" ^
  --input target\jpackage-input --main-jar FAM-app.jar --main-class com.linkline.fam.FAM ^
  --java-options "-Dfile.encoding=UTF-8" --dest dist %EXTRA% || exit /b 1
echo.
echo Done. See the dist folder.