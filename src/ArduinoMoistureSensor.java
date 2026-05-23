import org.eclipse.paho.client.mqttv3.*;

/**
 * @FileName    : ArduinoMoistureSensor.java
 * @Description : MQTT를 통해 아두이노 토양 수분 센서 값을 수신하는 구현체
 *                MoistureSensor 인터페이스를 구현한다.
 *
 * MQTT 토픽 규칙: "sensor/A{핀번호}" (예: sensor/A0, sensor/A1)
 * 브로커: Mosquitto (맥북에 설치, listener 1883 / allow_anonymous true 설정 필요)
 */
public class ArduinoMoistureSensor implements MoistureSensor {

    /** 공기 중 ADC 값의 최솟값. 이 이상이면 센서가 흙에 꽂히지 않은 것으로 판단 */
    private static final int AIR_THRESHOLD = 900;

    private final String sensorName;
    private int latestValue = 0;
    private boolean connected = false;

    /**
     * 생성 시 MQTT 브로커에 자동으로 연결하고 토픽을 구독한다.
     *
     * @param sensorName 센서 이름 (예: "A0_수분센서")
     * @param brokerIp   Mosquitto가 실행 중인 맥북의 내부 IP
     * @param topic      구독할 MQTT 토픽 (예: "sensor/A0")
     */
    public ArduinoMoistureSensor(String sensorName, String brokerIp, String topic) {
        this.sensorName = sensorName;
        connectAndSubscribe(brokerIp, topic);
    }

    private void connectAndSubscribe(String brokerIp, String topic) {
        try {
            String brokerUrl = "tcp://" + brokerIp + ":1883";
            MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            client.connect();
            connected = true;
            System.out.println("[MQTT] '" + sensorName + "' 연결 성공: " + brokerUrl);

            client.subscribe(topic, (t, msg) -> {
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

    /** @return 센서 이름 */
    @Override
    public String getSensorName() { return sensorName; }

    /**
     * MQTT로 수신된 최신 ADC 값을 반환한다.
     * 브로커 연결에 실패한 경우 테스트용 랜덤 값(0~1023)을 반환한다.
     *
     * @return 토양 수분 ADC 값 (높을수록 건조)
     */
    @Override
    public int readValue() {
        if (!connected) return (int)(Math.random() * 1023);
        return latestValue;
    }

    /**
     * 센서가 흙에 꽂혀 있는지 판단한다.
     * 용량성 센서는 공기 중에서 ADC 값이 AIR_THRESHOLD(900) 이상으로 올라간다.
     *
     * @return 정상 삽입 상태이면 true
     */
    @Override
    public boolean isInserted() {
        return latestValue < AIR_THRESHOLD;
    }
}
