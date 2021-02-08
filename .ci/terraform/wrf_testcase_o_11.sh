#!/bin/bash
su - ubuntu << 'EOF'
wget https://wrf-testcase-staging.s3.amazonaws.com/my_script.sh
mkdir /home/ubuntu/wrf-stuff
cd wrf-stuff/
git clone --branch leave_docker_images https://github.com/scala-computing/wrf-coop.git
cd wrf-coop/
sed -e "s^_GIT_URL_^$GIT_URL^" -e "s^_GIT_BRANCH_^$GIT_BRANCH^" Dockerfile-sed > Dockerfile
sed -e "s^_GIT_URL_^$GIT_URL^" -e "s^_GIT_BRANCH_^$GIT_BRANCH^" Dockerfile-sed-NMM > Dockerfile-NMM
csh build.csh /home/ubuntu/wrf-stuff/wrf-coop /home/ubuntu/wrf-stuff/wrf-coop

echo "==============================================================" >  OPENMP
echo "==============================================================" >> OPENMP
echo "                         OPENMP START" >> OPENMP
echo "==============================================================" >> OPENMP

date ; ./single_init.csh Dockerfile     wrf_regtest    > output_o_11 ; date 
 
./test_011.csh > outo & 

wait 
./single_end.csh wrf_regtest    >> output_o_11 ; date 
cat OPENMP outo >> output_o_11

rm outo
rm SERIAL OPENMP MPI 
EOF
