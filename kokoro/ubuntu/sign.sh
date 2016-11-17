#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

echo ${KOKORO_GFILE_DIR}
cd $KOKORO_GFILE_DIR

mkdir -p signed && chmod 777 signed
mkdir -p signed/plugins && chmod 777 signed/plugins
mkdir -p signed/features && chmod 777 signed/features
 
cp artifacts.jar signed/artifacts.jar
cp content.jar signed/content.jar
cp category.xml signed/category.xml

FILES=plugins/*.jar
for f in $FILES
do
  echo "Processing $f file..."
  filename=$(basename "$f")
  echo "Signing $filename"
  if /escalated_sign/escalated_sign.py -j /escalated_sign_jobs -t signjar \
    plugins/"$filename" \
    signed/plugins/"$filename"
  then echo "Signed $filename"
  else 
    cp "plugins/$filename" "signed/plugins/$filename"
  fi
done

FEATURES=features/*.jar
for f in $FEATURES
do
  echo "Processing $f file..."
  filename=$(basename "$f")
  echo "Signing $filename"
  /escalated_sign/escalated_sign.py -j /escalated_sign_jobs -t signjar \
    features/"$filename" \
    signed/features/"$filename"
  echo "Signed $filename"
done
