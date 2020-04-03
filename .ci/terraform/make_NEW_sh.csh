#!/bin/csh

#	This script makes the wrf_testcase_xx.sh files.

set TEST_NUM = (  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  )
set BUILDS   = ( som  m  sm  som som som som  m  som  s  som som som som som som som som )


set COUNT = 1
foreach f ( $TEST_NUM )

	set name = wrf_testcase_${f}.sh
	if ( -e $name ) then
		rm -f $name
	endif
	touch $name
		
	echo '#\!/bin/bash' >> $name
	echo "su - ubuntu << 'EOF'" >> $name
	echo "wget https://wrf-testcase.s3.amazonaws.com/my_script.sh" >> $name
	echo "mkdir /home/ubuntu/wrf-stuff" >> $name
	echo "cd wrf-stuff/" >> $name
	echo "git clone git@github.com:davegill/wrf-coop.git" >> $name
	echo "cd wrf-coop/" >> $name
	echo 'sed -e "s^_GIT_URL_^$GIT_URL^" -e "s/_GIT_BRANCH_/$GIT_BRANCH/" Dockerfile-sed > Dockerfile' >> $name
	echo 'sed -e "s^_GIT_URL_^$GIT_URL^" -e "s/_GIT_BRANCH_/$GIT_BRANCH/" Dockerfile-sed-NMM > Dockerfile-NMM' >> $name
	echo "csh build.csh /home/ubuntu/wrf-stuff/wrf-coop /home/ubuntu/wrf-stuff/wrf-coop" >> $name

	echo 'echo "==============================================================" >  SERIAL' >> $name
	echo 'echo "==============================================================" >> SERIAL' >> $name
	echo 'echo "                         SERIAL START" >> SERIAL' >> $name
	echo 'echo "==============================================================" >> SERIAL' >> $name
	echo "" >> $name

	echo 'echo "==============================================================" >  OPENMP' >> $name
	echo 'echo "==============================================================" >> OPENMP' >> $name
	echo 'echo "                         OPENMP START" >> OPENMP' >> $name
	echo 'echo "==============================================================" >> OPENMP' >> $name
	echo "" >> $name

	echo 'echo "==============================================================" >  MPI' >> $name
	echo 'echo "==============================================================" >> MPI' >> $name
	echo 'echo "                         MPI START" >> MPI' >> $name
	echo 'echo "==============================================================" >> MPI' >> $name
	echo "" >> $name

	if      ( $f <   10 ) then
		set NUM = 00$f
	else if ( $f <  100 ) then
		set NUM =  0$f
	else if ( $f < 1000 ) then
		set NUM =   $f
	endif
	set BASE = test_$NUM
	set NAMES = ${BASE}s.csh
	set NAMEO = ${BASE}o.csh
	set NAMEM = ${BASE}m.csh
	if ( $NUM == 002 ) then
		echo "date ; ./single.csh Dockerfile-NMM > output_$f ; date " >> $name
	else
		echo "date ; ./single.csh Dockerfile     > output_$f ; date " >> $name
	endif
	if      ( $BUILDS[$COUNT] == som ) then
		echo "./$NAMES > outs & " >> $name
		echo "./$NAMEO > outo & " >> $name
		echo "./$NAMEM > outm & " >> $name
		echo "wait " >> $name
		echo "cat SERIAL outs OPENMP outo MPI outm >> output_$f" >> $name
		echo "date ; ./last_only_once.csh >> output_$f ; date" >> $name
		echo "rm outs outo outm " >> $name
	else if ( $BUILDS[$COUNT] == sm ) then
		echo "./$NAMES > outs & " >> $name
		echo "./$NAMEM > outm & " >> $name
		echo "wait " >> $name
		echo "cat SERIAL outs MPI outm >> output_$f" >> $name
		echo "date ; ./last_only_once.csh >> output_$f ; date" >> $name
		echo "rm outs outm " >> $name
	else if ( $BUILDS[$COUNT] == s ) then
		echo "./$NAMES > outs & " >> $name
		echo "wait " >> $name
		echo "cat SERIAL outs >> output_$f" >> $name
		echo "date ; ./last_only_once.csh >> output_$f ; date" >> $name
		echo "rm outs " >> $name
	else if ( $BUILDS[$COUNT] == m ) then
		echo "./$NAMEM > outm & " >> $name
		echo "wait " >> $name
		echo "cat MPI outm >> output_$f" >> $name
		echo "date ; ./last_only_once.csh >> output_$f ; date" >> $name
		echo "rm outm " >> $name
	endif

	echo "rm SERIAL OPENMP MPI " >> $name
	echo "EOF" >> $name
	chmod +x $name
	@ COUNT ++
end
