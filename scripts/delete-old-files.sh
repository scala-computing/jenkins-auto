sudo find /var/lib/jenkins/workspace/WRF-Feature-Regression-Test -type d -mtime +90  -exec ls -lhatr {} \; > /tmp/folder.out
sudo find /var/lib/jenkins/workspace/WRF-Feature-Regression-Test -type d -mtime +90  -exec rm -rf {} +
echo "Number of files deleted " 
cat /tmp/folder.out | wc -l
echo "Files deleted are"
cat /tmp/folder.out
echo "Printing files in the directory after performing above delete operation"
ls -ltr /var/lib/jenkins/workspace/WRF-Feature-Regression-Test
sudo rm /tmp/folder.out