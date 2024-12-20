#!/bin/bash
su - ubuntu << 'EOF'
wget https://wrf-testcase.s3.amazonaws.com/upload_script.sh
mkdir /home/ubuntu/wrf-stuff
cd wrf-stuff/
git clone --branch regression+feature https://github.com/wrf-model/wrf-coop.git
cd wrf-coop/
sed -e "s^_GIT_URL_^$GIT_URL^" -e "s^_GIT_BRANCH_^$GIT_BRANCH^" Dockerfile-sed > Dockerfile
csh build.csh /home/ubuntu/wrf-stuff/wrf-coop /home/ubuntu/wrf-stuff/wrf-coop
echo "==============================================================" >  MPI
echo "==============================================================" >> MPI
echo "                         MPI START" >> MPI
echo "==============================================================" >> MPI

date ; ./single_init.csh Dockerfile     wrf_regtest    > output_18 ; date 
./test_007m.csh > outm 
./single_end.csh wrf_regtest    >> output_18 ; date 
cat MPI outm >> output_18
rm outm MPI 
date 
EOF
