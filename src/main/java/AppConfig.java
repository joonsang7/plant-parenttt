/**
 * @FileName    : AppConfig.java
 * @Description : 애플리케이션 설정값을 한 곳에서 관리하는 클래스
 *                네트워크, 센서 관련 설정을 변경할 때 이 파일만 수정하면 된다.
 */
public class AppConfig {

    /** MQTT 브로커(Mosquitto)가 실행 중인 PC의 IP 주소 */
    public static final String BROKER_IP = "192.168.35.111";

    /** MQTT 브로커 포트 */
    public static final int BROKER_PORT = 1883;

    private AppConfig() {} // 인스턴스 생성 방지
}
