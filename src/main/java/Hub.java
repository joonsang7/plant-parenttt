import java.util.*;
import java.util.concurrent.*;

/**
 * @FileName    : Hub.java
 * @Description : 모든 화분(PlantPot)을 관리하는 중앙 컨트롤러 (Singleton)
 *                ScheduledExecutorService로 각 화분의 체크 주기를 개별 관리한다.
 *                센서 데이터 확인 → 건조 판단 → GUI 알림 순서로 동작한다.
 */
public class Hub {

    /** 아두이노 A0~A5 핀 개수 = 최대 화분 수 */
    public static final int MAX_POTS = 6;

    private static Hub instance;

    private final Map<Integer, PlantPot> plantPots = new LinkedHashMap<>();
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(MAX_POTS);

    private ControlUI ui; 

    /** 생성자를 private으로 막아 외부에서 new Hub() 불가 (Singleton) */
    private Hub() {}

    /**
     * Hub 단일 인스턴스를 반환한다.
     *
     * @return Hub 인스턴스
     */
    public static Hub getInstance() {
        if (instance == null) instance = new Hub();
        return instance;
    }

    /** GUI 참조를 설정한다. 알림 표시에 사용된다. */
    public void setUI(ControlUI ui) {
        this.ui = ui;
    }

    /**
     * 새 화분을 등록하고 해당 화분의 센서 체크 스케줄을 시작한다.
     *
     * @param pin    아두이노 아날로그 핀 번호 (0~5)
     * @param plant  화분에 심긴 식물 객체
     * @param sensor 화분에 꽂힌 센서 객체
     * @return 등록 성공이면 true, 최대 수 초과 또는 핀 중복이면 false
     */
    public boolean addPlantPot(int pin, Plant plant, MoistureSensor sensor) {
        if (plantPots.size() >= MAX_POTS) {
            System.out.println("[Hub] 최대 화분 수(" + MAX_POTS + ")에 도달했습니다.");
            return false;
        }
        if (plantPots.containsKey(pin)) {
            System.out.println("[Hub] A" + pin + " 핀은 이미 사용 중입니다.");
            return false;
        }

        PlantPot pot = new PlantPot(pin, plant, sensor);
        plantPots.put(pin, pot);

        // 식물 종별 체크 주기로 스케줄 등록
        int intervalHours = plant.getSpecies().getCheckIntervalHours();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> checkPot(pot),
            0, intervalHours, TimeUnit.HOURS
        );
        scheduledTasks.put(pin, task);

        System.out.println("[Hub] 화분 추가 완료: " + plant + " | 핀 A" + pin
            + " | 체크 주기: " + intervalHours + "시간");
        return true;
    }

    /**
     * 화분을 제거하고 해당 스케줄을 중단한다. 핀이 다시 사용 가능해진다.
     *
     * @param pin 제거할 화분의 핀 번호
     */
    public void removePlantPot(int pin) {
        ScheduledFuture<?> task = scheduledTasks.remove(pin);
        if (task != null) task.cancel(false);

        PlantPot removed = plantPots.remove(pin);
        if (removed != null)
            System.out.println("[Hub] 화분 제거: " + removed.getPlant().getName() + " (A" + pin + ")");
    }

    /**
     * 현재 사용 가능한 핀 번호 목록을 반환한다.
     *
     * @return 아직 화분이 등록되지 않은 핀 번호 리스트
     */
    public List<Integer> getAvailablePins() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < MAX_POTS; i++) {
            if (!plantPots.containsKey(i)) available.add(i);
        }
        return available;
    }

    /**
     * 등록된 모든 화분의 정보를 반환한다. (GUI 갱신용)
     *
     * @return 핀 번호 → PlantPot 매핑 (읽기 전용)
     */
    public Map<Integer, PlantPot> getPlantPots() {
        return Collections.unmodifiableMap(plantPots);
    }

    // ── private ──────────────────────────────────────────────

    /**
     * 단일 화분의 센서를 읽고, 관수 필요 여부를 판단하여 GUI에 알린다.
     * 이미 알림을 보낸 화분에는 중복 알림을 발송하지 않는다.
     *
     * @param pot 체크할 화분
     */
    private void checkPot(PlantPot pot) {
        pot.updateSensorData();

        if (pot.needsWatering() && !pot.isNotified()) {
            String msg = "🌱 " + pot.getPlant().getName() + "의 수분이 부족합니다. 물을 주세요!";
            System.out.println("[알림] " + msg);
            pot.setNotified(true);
            if (ui != null) {
                // GUI 업데이트는 반드시 EDT(이벤트 디스패치 스레드)에서 실행
                javax.swing.SwingUtilities.invokeLater(() -> ui.showNotification(msg));
            }
        }

        // GUI 센서값 갱신
        if (ui != null) {
            javax.swing.SwingUtilities.invokeLater(() ->
                ui.refreshPotPanel(pot.getPinNumber(), pot.getLatestValue()));
        }
    }
}
