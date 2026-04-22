C:
cd \Files\Dev\Eclipse\Herd
set "mytime=%time: =0%"
set "mytime=%mytime::=%"
set "mytime=%mytime:~0,6%"
set BKP=%DATE:~6,4%%DATE:~3,2%%DATE:~0,2%_%mytime%
md bkp_%BKP%
copy data*.txt bkp_%BKP%
copy bkp_OneSample\*.* .