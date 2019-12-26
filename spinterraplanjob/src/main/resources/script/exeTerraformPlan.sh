#!/bin/bash

planDir=$1
variablefile=\"$2\"

cd $planDir
echo In shell script path :: $planDir

#terraform init -no-color


if [ $# -eq 2 ] 
then
   terraform init -var-file=$variablefile -no-color
   terraform plan -var-file=$variablefile -no-color
else
   terraform init -no-color
   terraform plan -no-color 
fi


echo :: Finish terraform plan part ::