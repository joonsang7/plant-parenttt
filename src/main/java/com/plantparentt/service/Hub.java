package com.plantparentt.service;

import com.plantparentt.config.AppConfig;
import com.plantparentt.model.PlantPot;
import com.plantparentt.sensor.ArduinoMoistureSensor;
import com.plantparentt.sensor.MoistureSensor;

import java.util.*;
import java.util.concurrent.*;

/**
 * @FileName : Hub.java
 * @Description : 모든 화분(PlantPot)을 관리하는 중앙 컨트롤러 (Singleton)
 *              ScheduledExecutorService로 각 화분의 체크 주기를 개별 관리
 *              센서 데이터 확인 → 건조 판단 → GUI 알림 순서로 동작한다
 */
public class Hub {

    /** 아두이노 아날로그 핀은 A0~A5 까지 존재하므로 최대 화분 수는 6개로 고정 */
    public static final int MAX_POTS = 6;

    // Eager initialization: JVM 클래스 로딩 시 단 한 번만 생성 → 별도 동기화 불필요
    // Singleton 패턴을 적용하여 Hub 클래스의 단일 인스턴스를 제공한다. 앱 전체에서 이 인스턴스를 공유하여 화분 관리와 센서 체크를
    // 중앙 집중식으로 처리한다. Hub는 ControlUI와 PlantPot 간의 중개자 역할을 하며, PlantMonitorView
    // 인터페이스를 통해 ControlUI에 알림과 갱신을 전달한다.
    // 하나만 존재하도록 하기 위해서 생성자를 private으로 막고, 클래스 로딩 시 단 한 번만 생성되는 INSTANCE 상수를 제공한다. 이렇게 하면 멀티스레드 환경에서도 안전하게 Singleton을 구현할 수 있다.
    private static final Hub INSTANCE = new Hub();


    // 핀 번호 → PlantPot 매핑 (화분 관리용)
    // plantPots는 화분을 담는 중앙 저장소 역할을 한다. Hub는 plantPots를 통해 각 핀에 어떤 화분이 등록되어 있는지
    // 관리한다. 화분 등록 시 센서 객체도 함께 전달하여 PlantPot이 센서 데이터를 직접 읽을 수 있도록 한다. Hub는 PlantPot과
    // 센서 객체를 함께 관리하며, 스케줄러로 주기적인 체크 작업을 등록한다.
    private final Map<Integer, PlantPot> plantPots = new LinkedHashMap<>();

    // 핀 번호 → 건조 체크 작업 매핑 (6시간 주기, 화분 제거 시 작업 취소용)
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    // 핀 번호 → 관수 감지 작업 매핑 (1분 주기, 건조 알림 발송 후 빠른 복구 감지용)
    private final Map<Integer, ScheduledFuture<?>> wateringTasks = new HashMap<>();

    // 건조 체크 + 관수 감지 스케줄을 함께 처리하기 위해 스레드 풀 크기를 MAX_POTS * 2로 설정
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(MAX_POTS * 2);

    private PlantMonitorView view;


    /** 생성자를 private으로 막아 외부에서 new Hub() 불가하도록 하기 (Singleton 패턴) */
    private Hub() {
    }

    /**
     * Hub 단일 인스턴스를 반환하는 정적 메서드
     *
     * @return Hub 인스턴스
     */
    public static Hub getInstance() {
        return INSTANCE;
    }

    /** UI 참조를 설정하는 메서드. 알림 표시에 사용된다. */
    public void setView(PlantMonitorView view) {
        this.view = view;
    }

    /**
     * 지정한 핀 번호에 대한 센서를 생성하고 반환하는 메서드. ControlUI에서 새 화분 등록 시 호출된다.
     * 현재 ArduinoMoistureSensor로 고정되어 있지만, 향후 다양한 센서 타입을 지원하기 위해 인터페이스 기반으로 설계(DIP)
     *
     * @param pin 아두이노 아날로그 핀 번호 (0~5)
     * @return 생성된 MoistureSensor 인스턴스
     */
    public MoistureSensor createSensor(int pin) {
        return new ArduinoMoistureSensor(
                "A" + pin + "_sensor", AppConfig.BROKER_IP, "sensor/A" + pin);
    }

    /**
     * 새 화분을 등록하고 해당 화분의 센서 체크 스케줄을 시작하는 메서드
     * 화분 등록 시 최대 수 초과 또는 핀 중복이면 false를 반환하여 GUI에서 사용자에게 알릴 수 있도록 한다. 성공 시 true 반환
     * 화분 등록 시 센서 객체를 생성하여 PlantPot에 전달한다. Hub는 PlantPot과 센서 객체를 함께 관리하며, 스케줄러로 주기적인
     * 체크 작업을 등록한다.
     *
     * @param pin       아두이노 아날로그 핀 번호 (0~5)
     * @param plantName 화분에 심긴 식물 이름
     * @param sensor    화분에 꽂힌 센서 객체
     * @return 등록 성공이면 true, 최대 수 초과 또는 핀 중복이면 false.
     */
    public boolean addPlantPot(int pin, String plantName, MoistureSensor sensor) {

        // 실패하는 경우: 최대 화분 수 초과 또는 핀 중복 → GUI에서 사용자에게 알릴 수 있도록 false 반환
        if (plantPots.size() >= MAX_POTS) {
            System.out.println("[Hub] 최대 화분 수(" + MAX_POTS + ")에 도달했습니다.");
            return false;
        }
        if (plantPots.containsKey(pin)) {
            System.out.println("[Hub] A" + pin + " 핀은 이미 사용 중입니다.");
            return false;
        }

        // 성공하는 경우: 화분 객체 생성 → 센서 체크 스케줄 등록 → true 반환
        PlantPot pot = new PlantPot(pin, plantName, sensor);
        // 등록 성공 시에는 plantPots에 화분을 추가한다. Hub는 PlantPot과 센서 객체를 함께 관리하며,
        // 스케줄러로 주기적인 체크 작업을 등록한다.
        plantPots.put(pin, pot);

        // 건조 감지: 고정 주기로 스케줄 등록 (이력 누적 → 24시간 유지 판단)


        /**
         * task 변수는 스케줄된 작업을 나타내는 ScheduledFuture<?> 객체로, 이 객체를 통해 작업의 상태를 추적하거나 취소할 수 있다.
         * Hub에서는 화분 제거 시 이 작업을 취소하기 위해 ScheduledFuture<?> 객체를 저장한다.
         * ScheduledFuture<?>는 스케줄된 작업이 Runnable인지 Callable인지에 관계없이 사용할 수 있다.

            runnable과 callable의 차이 :
             - Runnable: run() 메서드를 구현하며, 실행 후에 반환값이 없다. 단순히 작업을 수행하는 데 사용된다.
             - Callable: call() 메서드를 구현하며, 실행 후에 결과를 반환할 수 있다. 예외를 던질 수도 있다.

            Hub에서는 checkPot(pot) 메서드를 Runnable로 등록하여, 주기적으로 화분의 상태를 체크하지만, 
            이 작업이 완료된 후에 어떤 결과도 반환하지 않아야 하므로 Runnable을 사용하여 스케줄링한다
         * 
         */
        
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> checkPot(pot),
                0, AppConfig.CHECK_INTERVAL_HOURS, TimeUnit.HOURS);
        scheduledTasks.put(pin, task);

        // 관수 감지: 짧은 주기로 별도 스케줄 등록 (건조 알림 후 빠른 회복 감지)
        ScheduledFuture<?> wateringTask = scheduler.scheduleAtFixedRate(
                () -> checkWateringDetection(pot),
                AppConfig.WATERING_CHECK_INTERVAL_MINUTES,
                AppConfig.WATERING_CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        wateringTasks.put(pin, wateringTask);

        System.out.println("[Hub] 화분 추가 완료: " + pot.getPlantName() + " | 핀 A" + pin
                + " | 건조 체크: " + AppConfig.CHECK_INTERVAL_HOURS + "시간"
                + " | 관수 감지: " + AppConfig.WATERING_CHECK_INTERVAL_MINUTES + "분");
        return true;
    }

    /**
     * 화분을 제거하고 해당 스케줄을 중단함으로써 핀이 다시 사용 가능해진다
     * 화분 제거 시 센서 MQTT 구독도 해제하여 재등록 때 중복 구독이 발생하지 않도록 했습니다
     *
     * @param pin 제거할 화분의 핀 번호
     */
    public void removePlantPot(int pin) {
        ScheduledFuture<?> task = scheduledTasks.remove(pin);
        if (task != null)
            task.cancel(false);

        // 관수 감지 스케줄도 함께 취소
        ScheduledFuture<?> wateringTask = wateringTasks.remove(pin);
        if (wateringTask != null)
            wateringTask.cancel(false);

        PlantPot removed = plantPots.remove(pin);
        if (removed != null) {
            removed.disconnect(); // 센서 MQTT 구독 해제 — 미해제 시 재등록 때 중복 구독 발생하기 때문에 화분 제거 시 구독 해제하도록 했습니다
            System.out.println("[Hub] 화분 제거: " + removed.getPlantName() + " (A" + pin + ")");
        }
    }

    /**
     * 등록된 모든 화분의 정보를 반환하는 메서드 (GUI 갱신용)
     *
     * @return 핀 번호를 키로, PlantPot 객체를 값으로 하는 읽기 전용 맵.
     */
    public Map<Integer, PlantPot> getPlantPots() {
        return Collections.unmodifiableMap(plantPots);
    }

    /**
     * 특정 핀의 화분 센서를 즉시 체크하고 GUI를 갱신하는 메서드
     * 
     * ControlUI의 "수분량 체크" 버튼에서 호출
     * 건조 알림 발송 중이면 수분이 DRY_THRESHOLD 이상으로 회복되는 즉시 빨간색(건조 상태 알람)을 해제한다.
     * 아직 건조 알림이 없는 상태에서 DRY_THRESHOLD 미만이면 즉시 알림을 발송한다. 
     *
     * @param pin 체크할 핀 번호
     */
    public void checkPotNow(int pin) {
        PlantPot pot = plantPots.get(pin);
        if (pot == null)
            return; // 해당 핀에 화분이 등록되어 있지 않을 경우에는 아무 작업도 하지 않음

        checkPot(pot); // 건조 판단 + 이력 갱신 + GUI 수분값 갱신

        int latestValue = pot.getLatestValue();

        // ── 수동 관수 감지
        // ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
        // 건조 알림 발송 후 수분이 건조 기준 이상으로 회복되면 빨간색 즉시 해제
        // 자동 감지(WATERING_DETECTED_THRESHOLD=50%)보다 낮은 기준 적용 - 사용자가 직접 확인한 값이므로
        if (pot.isNotified() && latestValue >= AppConfig.DRY_THRESHOLD) {
            boolean wellWatered = latestValue >= AppConfig.WATERING_DETECTED_THRESHOLD;
            pot.resetAfterWatering();
            System.out.println("[수동 체크 관수 감지] " + pot.getPlantName()
                    + " 수분 " + latestValue + "% → 건조 알림 해제");
            if (view != null) {
                view.resetPotPanel(pin);
                if (wellWatered) {
                    String msg = "💧 " + pot.getPlantName() + " 관수가 감지되었습니다.";
                    view.showNotification(msg);
                }
            }
            return; // 관수 감지 완료 → 건조 즉시 알림 불필요
        }

        // ── 수동으로 즉시 건조 판단 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
        // 24시간 유지 조건 없이 현재 값이 낮으면 바로 알림
        if (!pot.isNotified() && pot.isCurrentlyDry()) {
            String msg = "🌱 " + pot.getPlantName() + "의 수분이 부족합니다. 물을 주세요!";
            System.out.println("[즉시 알림] " + msg);
            pot.setNotified(true);
            if (view != null) {
                view.showNotification(msg);
                view.markSlotDry(pin);
            }
        }
    }

    // ── private ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 단일 화분의 센서를 읽고, 건조 여부를 판단하여 GUI에 알리는 메서드
     * 
     * 이미 알림을 보낸 화분에는 중복 알림을 발송하지 않는 조건이 포함되어 있다. 이 메서드는 checkPotNow()에서 수동 체크용으로도
     * 호출되고, 스케줄러에서 주기적으로 자동 체크용으로도 호출된다.
     * 관수 감지는 checkWateringDetection()에서 별도 주기로 처리한다.
     *
     * @param pot 체크할 화분 객체
     */
    private void checkPot(PlantPot pot) {
        pot.updateSensorData();

        final int pin = pot.getPinNumber();

        // ── 건조 알림  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
        // 수분이 임계값 이하로 24시간 유지되면 알림 발송 (중복 방지)
        // race condition 발생 가능한 상황: checkPotNow()에서 수동 체크로 건조 알림 발송 → 거의 동시에 스케줄러에서 자동 체크로 건조 알림 발송 → 중복 알림 발생 가능 
        // 발생하려면 6시간 주기 스케줄러가 실행되는 짧은 시간 안에 사용자가 버튼을 눌러야 한다. 
        if (pot.needsWatering() && !pot.isNotified()) {
            String msg = "🌱 " + pot.getPlantName() + "의 수분이 부족합니다. 물을 주세요!";
            System.out.println("[알림] " + msg);

            pot.setNotified(true);
            if (view != null) {
                view.showNotification(msg);
                view.markSlotDry(pin);
            }
        }

        // View가 null이 아닌 경우 GUI 센서값 갱신하기 (화분 등록 직후, 수동 체크 시, 자동 체크 시)
        if (view != null) {
            view.refreshPotPanel(pin, pot.getLatestValue());
        }
    }

    /**
     * 건조 알림이 발송된 화분에 대해 관수 여부를 즉시 확인하는 메서드
     * checkPot()의 6시간 주기와 별개로 1분마다 실행되어 물을 준 직후 빠르게 상태를 체크 할 수 있도록 한다. 
     * 이 메서드는 checkPotNow()에서 수동 체크용으로도 호출되고, 스케줄러에서 주기적으로 자동 체크용으로도 호출된다.
     * 
     * 이력에는 추가하지 않고 센서 현재값만 직접 읽어 가볍게 처리
     *
     * @param pot 체크할 화분 객체
     */
    private void checkWateringDetection(PlantPot pot) {
        if (!pot.isNotified())
            return; // 건조 알림이 발송된 상태가 아니면 관수 감지 불필요

        int currentPercent = pot.getCurrentSensorPercent();
        if (currentPercent < 0)
            return; // 센서 미수신 또는 미보정

        final int pin = pot.getPinNumber();

        if (currentPercent >= AppConfig.WATERING_DETECTED_THRESHOLD) {
            pot.resetAfterWatering();
            String msg = "💧 " + pot.getPlantName() + " 관수가 감지되었습니다.";
            System.out.println("[관수 감지] " + msg);
            if (view != null) {
                view.showNotification(msg);
                view.resetPotPanel(pin);
            }
        }
    }
}
