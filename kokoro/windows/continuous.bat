@echo on
cd github\google-cloud-eclipse

set CLOUDSDK_CORE_DISABLE_USAGE_REPORTING=true
call gcloud.cmd components update --quiet
@echo on
call gcloud.cmd components install app-engine-java --quiet
@echo on

mvn -B --settings kokoro\windows\m2-settings.xml ^
    -N io.takari:maven:wrapper -Dmaven=3.5.0

cd plugins/com.google.cloud.tools.eclipse.integration.appengine/test-projects
for %%i in (*.zip) do jar xvf %%i

for /r . %%i in (pom.xml) do (
    echo %%i
    echo %%~fi
    echo %%~dpnxi
    )
dir /s
cd ../../..

mvnw.cmd -B --settings kokoro\windows\m2-settings.xml ^
         --fail-at-end -Ptravis -Declipse.target=oxygen verify

exit /b %ERRORLEVEL%
