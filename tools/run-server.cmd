@echo off
REM Double-click this to start the case runner the sheet's RUN links call.
REM Leave the window open while you work from the sheet; close it to stop.
title Admin weibo case runner
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-server.ps1" %*
