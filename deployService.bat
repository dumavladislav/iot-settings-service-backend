
call mvn clean
call mvn install

REM Copy files to server
ssh dumavla@192.168.1.53 "rm -f -r iot-settings-service-backend && mkdir iot-settings-service-backend"
scp -r * dumavla@192.168.1.53:~/iot-settings-service-backend


REM docker build
REM ssh -t dumavla@192.168.1.53 "sudo docker build -t dumskyhome/iot-settings-service-backend:rev2 ~/iot-settings-service-backend/."
REM ssh -t dumavla@192.168.1.53 "sudo docker run --name iot-settings-service-backend-rev2  --restart unless-stopped -it -d dumskyhome/iot-settings-service-backend:rev2

ssh -t dumavla@192.168.1.53 "sudo docker stop iot-settings-service-backend-rev2 || true && sudo docker rm iot-settings-service-backend-rev2 || true && sudo docker build -t dumskyhome/iot-settings-service-backend:rev2 ~/iot-settings-service-backend/. && sudo docker run --name iot-settings-service-backend-rev2  --restart unless-stopped -it -d dumskyhome/iot-settings-service-backend:rev2"