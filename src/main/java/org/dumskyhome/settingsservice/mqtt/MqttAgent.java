package org.dumskyhome.settingsservice.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dumskyhome.settingsservice.json.JsonSettingsRequestData;
import org.dumskyhome.settingsservice.json.JsonSettingsRequestMessage;
import org.dumskyhome.settingsservice.service.SettingsService;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:mqtt.properties")
public class MqttAgent implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttAgent.class);

    private ObjectMapper objectMapper;
    private MqttClient mqttClient;

    @Value("${mqtt.clientId}")
    private String mqttClientId;

    @Autowired
    Environment env;

    @Autowired
    private SettingsService settingsService;

    MqttAgent() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        objectMapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
    }



    private void init() {
        try {
            mqttClient = new MqttClient(env.getProperty("mqtt.serverUrl"), mqttClientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean connect() {

        try {
            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setUserName(env.getProperty("mqtt.user"));
            mqttConnectOptions.setPassword(env.getProperty("mqtt.password").toCharArray());
            mqttConnectOptions.setConnectionTimeout(Integer.parseInt(env.getProperty("mqtt.connectionTimeout")));
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setCleanSession(true);

            mqttClient.connect(mqttConnectOptions);
            //if (mqttClient.isConnected()) executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
            return mqttClient.isConnected();

        } catch (MqttException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean subscribeToTopics() {
        try {
            mqttClient.subscribe(env.getProperty("mqtt.topic.settingsRequests"));
            mqttClient.setCallback(this);
            return true;
        } catch (MqttException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean runMqttService() {
        init();
        if(connect()) {
            logger.info("Connected to MQTT");
            return subscribeToTopics();
        }
        return false;
    }

    public void sendMessage(String topic, String messageString, int QoS) {
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(QoS);
        mqttMessage.setPayload(messageString.getBytes());
        try {
            mqttClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        logger.error("CONNECTION LOST");
        while(!connect()) {
            logger.info("RECONNECTION ATTEMPT");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        logger.error("CONNECTION RESTORED");
        subscribeToTopics();
    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        logger.info("TOPIC:"+ s + " || MESSAGE RECEIVED: " + mqttMessage.toString());
        if (s.equals(env.getProperty("mqtt.topic.settingsRequests"))) {
            logger.info("Settings Request received. Parsing....");
            JsonSettingsRequestMessage jsonSettingsRequestMessage = objectMapper.readValue(mqttMessage.toString(), JsonSettingsRequestMessage.class);

            logger.info(jsonSettingsRequestMessage.getHeader().getMacAddress());
            settingsService.provideDeviceSettings(jsonSettingsRequestMessage.getHeader()).<ResponseEntity>thenApply(ResponseEntity::ok);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }
}
