import org.eclipse.paho.client.mqttv3.*;

/**
 * MQTT로 아두이노 센서 값을 받아오는 클래스
 * Sensor를 상속받아 readValue()만 오버라이드 → 기존 Hub 구조 그대로 사용 가능
 *
 * 사용 라이브러리: Eclipse Paho MQTT (pom.xml 또는 jar 추가 필요)
 */
public class MqttSensor extends Sensor {

    private int latestValue = 0;
    private boolean connected = false;

    /**
     * @param sensorName 센서 이름 (예: "수분센서")
     * @param brokerIp   Mosquitto 가 설치된 노트북 IP
     * @param topic      구독할 MQTT 토픽 (예: "home/화분/moisture")
     */
    public MqttSensor(String sensorName, String brokerIp, String topic) {
        super(sensorName);
        connectAndSubscribe(brokerIp, topic);
    }
    
    

    private void connectAndSubscribe(String brokerIp, String topic) {
        try {
            String brokerUrl = "tcp://" + brokerIp + ":1883";
            MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());

            client.connect();
            connected = true;
            System.out.println("[MQTT] '" + getSensorName() + "' 브로커 연결 성공: " + brokerUrl);

            // 토픽 구독 — 아두이노가 값을 publish하면 여기서 받음
            client.subscribe(topic, (t, msg) -> {
                String payload = new String(msg.getPayload());
                try {
                    latestValue = Integer.parseInt(payload.trim());
                    System.out.println("[MQTT 수신] " + getSensorName() + " = " + latestValue);
                } catch (NumberFormatException e) {
                    System.out.println("[MQTT 오류] 숫자가 아닌 값 수신: " + payload);
                }
            });

        } catch (MqttException e) {
            System.out.println("[MQTT 연결 실패] " + getSensorName() + " - " + e.getMessage());
            System.out.println("  → 가짜 랜덤 값으로 동작합니다.");
        }
    }

    /**
     * Hub가 호출하는 메서드 — MQTT로 받은 최신값 반환
     * 연결 실패 시 랜덤값 반환 (테스트용 fallback)
     */
    @Override
    public int readValue() {
        if (!connected) {
            return (int)(Math.random() * 100);
        }
        return latestValue;
    }
}
