/**
 * @FileName    : plant_sensor.ino
 * @Description : 토양 수분 센서값을 읽어 MQTT 브로커로 전송하는 Arduino 스케치
 *                Java 식집사 앱(Hub.java)과 연동된다.
 *
 * @Board       : Arduino UNO R4 WiFi
 * @Library     : PubSubClient (Arduino IDE → 라이브러리 매니저 → "PubSubClient" 검색 후 설치)
 *
 * MQTT 토픽 규칙 : sensor/A{핀번호} (예: sensor/A0)
 * 메시지 형식    : ADC 정수값 문자열 (예: "432")
 * ADC 범위      : 0~1023 (10bit), 높을수록 건조
 */

#include <WiFiS3.h>      // Arduino UNO R4 WiFi 전용 라이브러리
#include <PubSubClient.h>

// ── WiFi 설정 ──────────────────────────────────────────────────
const char* WIFI_SSID     = "SK_WiFiGIGA359A";      // ← 본인 WiFi 이름
const char* WIFI_PASSWORD = "1803006166";   // ← 본인 WiFi 비밀번호

// ── MQTT 브로커 설정 ────────────────────────────────────────────
const char* BROKER_IP   = "192.168.35.117";  // ← Mosquitto가 실행 중인 PC/맥북 IP
const int   BROKER_PORT = 1883;
const char* CLIENT_ID   = "uno-r4-plant-sensor";

// ── 센서 핀 & 토픽 설정 ─────────────────────────────────────────
// UNO R4 WiFi는 A0~A5 핀을 그대로 사용 (Java 코드 핀 번호와 1:1 대응)
const int  SENSOR_COUNT  = 6;
const int  SENSOR_PINS[] = {A0, A1, A2, A3, A4, A5}; 
const char* TOPICS[]     = {
    "sensor/A0",
    "sensor/A1",
    "sensor/A2", 
    "sensor/A3",
    "sensor/A4",
    "sensor/A5"
};

// ── 센서 읽기 주기 ──────────────────────────────────────────────
const unsigned long READ_INTERVAL_MS = 5000; // 5초마다 전송

// ── 미연결 감지 설정 ────────────────────────────────────────────
const int NO_SENSOR_MARKER  = -1;   // 미연결 시 Java로 전송할 특수값
const int PULLDOWN_THRESHOLD = 10;  // raw 값이 이 이하이면 미연결로 판단

// ── 전역 객체 ───────────────────────────────────────────────────
WiFiClient   wifiClient;
PubSubClient mqttClient(wifiClient);

unsigned long lastReadTime = 0;

// ═══════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    Serial.println("\n[식집사 센서 시작]");

    // 내부 저항 없이 순수 입력 모드로 설정 (외부 풀다운 저항만 사용)
    // INPUT_PULLDOWN은 UNO R4에서 지원 안 되므로 INPUT 사용
    for (int i = 0; i < SENSOR_COUNT; i++) {
        pinMode(SENSOR_PINS[i], INPUT);
    }

    connectWiFi();

    mqttClient.setServer(BROKER_IP, BROKER_PORT);
    connectMQTT();
}

// ═══════════════════════════════════════════════════════════════
void loop() {
    // MQTT 연결 끊기면 재연결
    if (!mqttClient.connected()) {
        connectMQTT();
    }
    mqttClient.loop(); // MQTT 내부 처리 유지

    // 일정 주기마다 센서값 읽고 전송
    unsigned long now = millis();
    if (now - lastReadTime >= READ_INTERVAL_MS) {
        lastReadTime = now;
        readAndPublishAll();
    }
}


// ── WiFi 연결 함수───────────────────────────────────────────────────
void connectWiFi() {
    Serial.print("WiFi 연결 중: ");
    Serial.println(WIFI_SSID);

    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("\nWiFi 연결 완료!");
    Serial.print("Arduino IP: ");
    Serial.println(WiFi.localIP());
}

// ── MQTT 연결 함수───────────────────────────────────────────────────
void connectMQTT() {
    while (!mqttClient.connected()) {
        Serial.print("MQTT 브로커 연결 중... ");
        if (mqttClient.connect(CLIENT_ID)) {
            Serial.println("연결 완료!");
        } else {
            Serial.print("실패 (rc=");
            Serial.print(mqttClient.state());
            Serial.println("), 3초 후 재시도");
            delay(3000);
        }
    }
}

// ── 전체 센서 읽기 & 전송 함수  ───────────────────────────────────────
void readAndPublishAll() {

    // for문 사용해서 A0 ~ A5 까지 센서값 읽고 출력
    for (int i = 0; i < SENSOR_COUNT; i++) {
        analogRead(SENSOR_PINS[i]); // 이전 채널 잔류 전압 제거 (ADC 크로스토크 방지)
        delay(2);                   // 커패시터 안정화 대기하기
        int raw   = analogRead(SENSOR_PINS[i]);
        int value = (raw <= PULLDOWN_THRESHOLD) ? NO_SENSOR_MARKER : (1023 - raw);

        char payload[8];
        itoa(value, payload, 10);

        mqttClient.publish(TOPICS[i], payload);

        // 출력 부분
        Serial.print("[");
        Serial.print(TOPICS[i]);
        if (value == NO_SENSOR_MARKER) {
            Serial.println("] 센서 미연결");
        } else {
            Serial.print("] ADC: ");
            Serial.println(value);
        }
        Serial.println("---------------------------------------------------");
    }
}
