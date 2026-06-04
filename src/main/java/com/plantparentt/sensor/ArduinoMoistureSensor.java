package com.plantparentt.sensor;

import com.plantparentt.config.AppConfig;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * @FileName    : ArduinoMoistureSensor.java
 * @Description : MQTT를 통해 아두이노 토양 수분 센서 값을 수신하는 구현체
 *                MoistureSensor 인터페이스를 구현하며, MQTT 브로커에 연결하여 지정된 토픽에서 ADC 값을 수신합니다
 *
 * 
 
 * MQTT 토픽 규칙: "sensor/A{핀번호}" (예: sensor/A0, sensor/A1)
 * 브로커: Mosquitto (Ip: AppConfig.BROKER_IP, Port: AppConfig.BROKER_PORT)
 */
public class ArduinoMoistureSensor implements MoistureSensor {


    private final String sensorName;
    private MqttClient client;

    // volatile를 통해 MQTT 콜백에서 latestValue가 업데이트되면, 메인 스레드에서 readValue()를 호출할 때 항상 최신 값이 읽히도록 하기 위해 volatile로 선언
    private volatile int latestValue = AppConfig.NO_SENSOR_MARKER;
    private volatile boolean connected = false;

    // ── 보정값 ──────────────────────────────────────────────────
    private int     dryValue   = -1;     // 공기 중 ADC (0% 기준)
    private int     wetValue   = -1;     // 물 속 ADC  (100% 기준)
    private boolean calibrated = false;

    /**
     * 생성자. 생성 시 MQTT 브로커에 자동으로 연결하고 토픽을 구독합니다
     *
     * @param sensorName 센서 이름 (예: "화분 A0"), 로그 메시지에 사용되게 됩니다
     * @param brokerIp   Mosquitto가 실행 중인 기기의 내부 IP
     * @param topic      구독할 MQTT 토픽 (예: "sensor/A0")
     */
    public ArduinoMoistureSensor(String sensorName, String brokerIp, String topic) {
        this.sensorName = sensorName;
        connectAndSubscribe(brokerIp, topic);
    }

    private void connectAndSubscribe(String brokerIp, String topic) {
        try {
            String brokerUrl = "tcp://" + brokerIp + ":" + AppConfig.BROKER_PORT;
            client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
            client.connect();
            connected = true;
            System.out.println("[MQTT] '" + sensorName + "' 연결 성공: " + brokerUrl);

            // 토픽 구독 및 메시지 수신 처리
            // subscribe는 MQTT 구독을 담당하는 Paho MQTT 클라이언트 라이브러리의 메서드 입니다. subscribe(topic, qos, callback) 형태로 사용
            // 구독은 QoS 0으로 구독 (최대 한 번 전달, 순서 보장 없음)하도록 했습니다. 
            client.subscribe(topic, 0, (t, msg) -> {
                String payload = new String(msg.getPayload());
                try {
                    latestValue = Integer.parseInt(payload.trim());
                    System.out.println("[MQTT 수신] " + sensorName + " = " + latestValue);
                } catch (NumberFormatException e) {
                    System.out.println("[MQTT 오류] 숫자가 아닌 값 수신: " + payload);
                }
            });

        } catch (MqttException e) {
            System.out.println("[MQTT 연결 실패] " + sensorName + " - " + e.getMessage());
            System.out.println("  → 테스트용 랜덤 값으로 동작합니다.");
        }
    }




    /**
     * MQTT 브로커에 연결된 상태인지 반환하는 메서드
     *
     * @return 연결 중이면 true, 연결 실패 또는 해제 상태이면 false
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * MQTT로 수신된 최신 ADC 값을 반환하는 메서드
     * 브로커 연결에 실패한 경우 NO_SENSOR_MARKER(-1)를 반환
     *
     * @return 토양 수분 ADC 값, 미연결·미수신이면 -1
     */
    @Override
    public int readValue() {
        if (!connected) return AppConfig.NO_SENSOR_MARKER;    // 연결 실패 시 랜덤 값 대신 NO_SENSOR_MARKER( == -1) 반환하도록 변경했습니다
        return latestValue;
    }

    /**
     * 센서가 흙(또는 물)에 꽂혀 있는지 판단하는 메서드
     * 꽂혀있는지 판단하는 단계는 보정 이후에 진행되기 때문에, 
     *
     * @return 정상 삽입 상태이면 true
     */
    @Override
    public boolean isInserted() {
        if (latestValue == AppConfig.NO_SENSOR_MARKER) return false;// MQTT 연결 실패 또는 미수신 상태에서는 센서가 꽂혀있지 않은 것으로 간주하도록 했습니다 
        return latestValue > dryValue;                              // 아두이노 센서가 꽂혀있는지 판단하는 메서드는 항상 보정 이후에 진행되기 때문에, 보정된 dryValue보다 최신 ADC 값이 높으면 꽂혀있는 것으로 판단하도록 했습니다
    }

    /**
     * 공기 중(건조)과 물 속(포화) ADC 값으로 센서를 보정하는 메서드
     *
     * @param dryValue 공기 중 ADC 값  (0% 기준)
     * @param wetValue 물 속   ADC 값  (100% 기준)
     */
    @Override
    public void setCalibration(int dryValue, int wetValue) {
        this.dryValue   = dryValue;
        this.wetValue   = wetValue;
        this.calibrated = true;
        System.out.println("[보정] " + sensorName
            + " | 건조=" + dryValue + " | 포화=" + wetValue);
    }

    /**
     * 보정값을 적용한 수분 퍼센트를 반환하는 메서드
     *
     * @return 0~100 범위의 수분 %, 보정 전이거나 미수신이면 -1
     */
    @Override
    public int getMoisturePercent() {
        if (!calibrated || latestValue == AppConfig.NO_SENSOR_MARKER) return -1;
        int percent = (latestValue - dryValue) * 100 / (wetValue - dryValue);
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * MQTT 연결을 해제하기 위한 메서드
     * 식물 생성 취소, 실패, 또는 화분 삭제 시 MQTT 중복 구독 방지를 위해 호출
     * 
     * disconnect(0) : quiesce=0 으로 즉시 연결 해제한다
     * close()       : Paho 내부 콜백 스레드 등 리소스 완전 해제하기 위함
     */
    @Override
    public void disconnect() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    // disconnect() 메서드도 역시 Paho MQTT 클라이언트 라이브러리의 메서드입니다
                    client.disconnect(100); // 100ms 대기 후 연결 해제하여, MQTT 메시지 처리 중인 경우에도 최대한 안전하게 연결을 종료하도록 했습니다
                }
                client.close(); // 내부 콜백 스레드 등 리소스 완전 해제
                System.out.println("[MQTT] '" + sensorName + "' 연결 해제");
            } catch (MqttException e) {
                System.out.println("[MQTT] 연결 해제 실패: " + e.getMessage());
            }
        }
        connected = false;
    }
}
