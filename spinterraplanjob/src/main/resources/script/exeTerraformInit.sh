#!/bin/bash

planDir=$1
variablefile=\"$2\"

cd $planDir
echo In shell script path :: $planDir

#terraform init -no-color


if [ $# -eq 2 ] 
then
   echo :: In terraform init part with variable overide ::
   terraform init -var-file=$variablefile -no-color
else
   echo :: In terraform init part without variable overide ::
   terraform init -no-color 
fi


echo :: Finish terraform init part ::
