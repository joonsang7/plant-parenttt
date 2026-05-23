/**
 * @FileName    : MessageListener.java
 * @Description : MQTT 메시지 수신 콜백 인터페이스
 *                Hub가 이를 구현하여 MqttClient로부터 센서 데이터를 전달받는다.
 */
public interface MessageListener {
 
    /**
     * MQTT 메시지가 수신될 때 호출된다.
     *
     * @param topic   수신된 토픽 (예: "sensor/A0")
     * @param payload 수신된 메시지 내용 (예: "432")
     */
    void onMessage(String topic, String payload);
}
 