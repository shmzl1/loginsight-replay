@echo off
java -cp "%~dp0target\loginsight-app-0.1.0-SNAPSHOT.jar;%~dp0target\lib\*" org.loginsight.app.ReplayServer %*
