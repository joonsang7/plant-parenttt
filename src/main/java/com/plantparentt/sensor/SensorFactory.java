package com.plantparentt.sensor;

import com.plantparentt.config.AppConfig;

/**
 * @FileName    : SensorFactory.java
 * @Description : MoistureSensor 구현체 생성을 전담하는 팩토리 클래스
 *                Hub과 ControlUI가 ArduinoMoistureSensor를 직접 생성하지 않도록 분리한다. (DIP)
 */
public class SensorFactory {

    private SensorFactory() {} // 인스턴스 생성 방지

    /**
     * 지정한 핀 번호에 대한 MQTT 수분 센서를 생성하고 반환한다.
     * 센서 이름은 "A{핀번호}_sensor" 형식으로 자동 지정된다. (예: A0_sensor)
     *
     * @param pin 아두이노 아날로그 핀 번호 (0~5)
     * @return 생성된 MoistureSensor 인스턴스
     */
    public static MoistureSensor create(int pin) {
        return new ArduinoMoistureSensor(
            "A" + pin + "_sensor", AppConfig.BROKER_IP, "sensor/A" + pin);
    }
}
