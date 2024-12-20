#!/bin/bash
su - ubuntu << 'EOF'
wget https://wrf-testcase.s3.amazonaws.com/upload_script.sh
mkdir /home/ubuntu/wrf-stuff
cd wrf-stuff/
git clone --sbranch regression+feature https://github.com/wrf-model/wrf-coop.git
cd wrf-coop/
sed -e "s^_GIT_URL_^$GIT_URL^" -e "s^_GIT_BRANCH_^$GIT_BRANCH^" Dockerfile-sed > Dockerfile
csh build.csh /home/ubuntu/wrf-stuff/wrf-coop /home/ubuntu/wrf-stuff/wrf-coop
echo "==============================================================" >  SERIAL
echo "==============================================================" >> SERIAL
echo "                         SERIAL START" >> SERIAL
echo "==============================================================" >> SERIAL

date ; ./single_init.csh Dockerfile     wrf_regtest    > output_10 ; date 
./test_005s.csh > outs 
./single_end.csh wrf_regtest    >> output_10 ; date 
cat SERIAL outs >> output_10
rm outs SERIAL 
date 
EOF
