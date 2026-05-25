import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.Map;

/**
 * @FileName    : ControlUI.java
 * @Description : 식집사 메인 GUI 클래스 (Java Swing)
 *                화분 슬롯 6칸 표시, 식물 생성/삭제/물주기 기능, 알림 패널을 제공한다.
 */
public class ControlUI extends JFrame {

    private static final int MAX_POTS = Hub.MAX_POTS;

    private final Hub hub;

    // UI 컴포넌트
    private final JPanel[]  slotPanels    = new JPanel[MAX_POTS];
    private final JLabel[]  nameLabels    = new JLabel[MAX_POTS];
    private final JLabel[]  valueLabels   = new JLabel[MAX_POTS];
    private final JButton[] actionButtons = new JButton[MAX_POTS]; // 식물 생성 or 삭제
    private final JButton[] waterButtons  = new JButton[MAX_POTS]; // 물 줬음
    private final JTextArea notificationArea;


    // ── 생성자 ─────────────────────────────────────────────
    // Hub 인스턴스를 주입받아 GUI를 초기화한다.
    // Hub는 ControlUI에 대한 참조를 갖게 되고, ControlUI는 Hub의 상태를 반영하여 화면을 갱신
    public ControlUI(Hub hub) {
        this.hub = hub;
        hub.setUI(this);

        // ── 프레임 설정 ───────────────────────────────────────────
        setTitle("식집사 🌿");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── 상단: 제목 ─────────────────────────────────────
        JLabel title = new JLabel("🌱 식집사", JLabel.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(new EmptyBorder(12, 0, 4, 0));
        add(title, BorderLayout.NORTH);

        // ── 중앙: 화분 슬롯 6칸 ────────────────────────────
        JPanel slotsPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        slotsPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        for (int i = 0; i < MAX_POTS; i++) {
            slotsPanel.add(buildSlotPanel(i));
        }
        add(slotsPanel, BorderLayout.CENTER);

        // ── 하단: 알림 영역 ─────────────────────────────────
        notificationArea = new JTextArea(4, 0);
        notificationArea.setEditable(false);
        notificationArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        notificationArea.setText("알림이 여기에 표시됩니다.\n");
        JScrollPane scroll = new JScrollPane(notificationArea);
        scroll.setBorder(BorderFactory.createTitledBorder("알림"));
        scroll.setPreferredSize(new Dimension(0, 110));
        add(scroll, BorderLayout.SOUTH);

        setVisible(true);
    }





    // ── 슬롯 패널 생성 ────────────────────────────────────────

    private JPanel buildSlotPanel(int pin) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("A" + pin));
        panel.setBackground(new Color(245, 245, 240));

        // 식물 이름 / 수분값
        nameLabels[pin]  = new JLabel("(비어있음)", JLabel.CENTER);
        valueLabels[pin] = new JLabel("수분: --", JLabel.CENTER);
        nameLabels[pin].setFont(new Font("SansSerif", Font.BOLD, 13));
        valueLabels[pin].setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setOpaque(false);
        infoPanel.add(nameLabels[pin]);
        infoPanel.add(valueLabels[pin]);

        // 버튼 영역
        actionButtons[pin] = new JButton("식물 생성");
        waterButtons[pin]  = new JButton("물 줬음 💧");
        waterButtons[pin].setEnabled(false);
        waterButtons[pin].setVisible(false);

        final int p = pin;
        actionButtons[pin].addActionListener(e -> onActionButton(p));
        waterButtons[pin].addActionListener(e -> onWaterButton(p));

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(actionButtons[pin]);
        btnPanel.add(waterButtons[pin]);

        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(btnPanel,  BorderLayout.SOUTH);

        slotPanels[pin] = panel;
        return panel;
    }

    // ── 버튼 핸들러 ────────────────────────────────────────────

    /** "식물 생성" 또는 "삭제" 버튼 클릭 처리 */
    private void onActionButton(int pin) {
        if (hub.getPlantPots().containsKey(pin)) {
            // 현재 화분이 있으면 → 삭제
            int choice = JOptionPane.showConfirmDialog(this,
                "A" + pin + " 화분을 삭제하시겠습니까?", "화분 삭제", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                hub.removePlantPot(pin);
                setSlotEmpty(pin);
            }
        } else {
            // 비어있으면 → 식물 생성 다이얼로그
            showCreateDialog(pin);
        }
    }

    /** "물 줬음" 버튼 클릭 처리 */
    private void onWaterButton(int pin) {
        PlantPot pot = hub.getPlantPots().get(pin);
        if (pot != null) {
            pot.resetAfterWatering();
            valueLabels[pin].setForeground(Color.DARK_GRAY);
            waterButtons[pin].setEnabled(false);
            showNotification("💧 " + pot.getPlant().getName() + "에 물을 주었습니다.");
        }
    }

    /** 식물 생성 다이얼로그 표시 (2점 보정 포함) */
    private void showCreateDialog(int pin) {
        // ── MQTT 연결 및 센서 객체 생성 ─────────────────────────
        MoistureSensor sensor = new ArduinoMoistureSensor(
            "A" + pin + "_sensor", AppConfig.BROKER_IP, "sensor/A" + pin);

        // ── 보정 1단계: 공기 중 (건조 기준, 0%) ────────────────
        JOptionPane.showMessageDialog(this,
            "【보정 1단계 - 건조 기준】\n\n" +
            "① 센서를 공기 중(흙 밖)에 두세요.\n" +
            "② 5초 이상 기다리세요. (아두이노 전송 주기)\n" +
            "③ 확인을 누르면 현재 값이 건조 기준으로 기록됩니다.",
            "센서 보정", JOptionPane.INFORMATION_MESSAGE);

        int dryValue = sensor.readValue();
        if (dryValue < 0) {
            JOptionPane.showMessageDialog(this,
                "센서 값을 수신하지 못했습니다.\n" +
                "아두이노가 연결되어 있는지 확인하고 다시 시도하세요.",
                "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ── 보정 2단계: 물 속 (포화 기준, 100%) ────────────────
        JOptionPane.showMessageDialog(this,
            "【보정 2단계 - 포화 기준】\n\n" +
            "① 센서를 물에 완전히 담그세요.\n" +
            "② 5초 이상 기다리세요.\n" +
            "③ 확인을 누르면 현재 값이 포화 기준으로 기록됩니다.\n\n" +
            "(건조 기준값: " + dryValue + ")",
            "센서 보정", JOptionPane.INFORMATION_MESSAGE);

        int wetValue = sensor.readValue();
        if (wetValue < 0 || wetValue <= dryValue + 50) {
            JOptionPane.showMessageDialog(this,
                "보정 값이 올바르지 않습니다.\n" +
                "물 속 값이 공기 중 값보다 충분히 높아야 합니다.\n\n" +
                "공기 중: " + dryValue + " / 물 속: " + wetValue,
                "보정 오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        sensor.setCalibration(dryValue, wetValue);

        // ── 식물 이름 입력 ───────────────────────────────────────
        String name = JOptionPane.showInputDialog(this,
            "식물 이름을 입력하세요. (생략 시 \"내 식물\"로 대체)",
            "식물 생성", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return; // 취소

        // ── 센서 삽입 안내 ───────────────────────────────────────
        int ready = JOptionPane.showConfirmDialog(this,
            "센서를 화분에 꽂은 후 확인을 누르세요.",
            "센서 삽입", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (ready != JOptionPane.OK_OPTION) return;

        // ── 등록 ────────────────────────────────────────────────
        Plant plant = new Plant(name);
        if (hub.addPlantPot(pin, plant, sensor)) {
            setSlotOccupied(pin, plant.toString());
        }
    }

    // ── 슬롯 상태 변경 ──────────────────────────────────────────

    private void setSlotEmpty(int pin) {
        nameLabels[pin].setText("(비어있음)");
        valueLabels[pin].setText("수분: --");
        valueLabels[pin].setForeground(Color.DARK_GRAY);
        actionButtons[pin].setText("식물 생성");
        waterButtons[pin].setVisible(false);
        slotPanels[pin].setBackground(new Color(245, 245, 240));
    }

    private void setSlotOccupied(int pin, String plantName) {
        nameLabels[pin].setText(plantName);
        valueLabels[pin].setText("수분: 측정 중...");
        actionButtons[pin].setText("삭제");
        waterButtons[pin].setVisible(true);
    }

    // ── Hub가 호출하는 공개 메서드 ───────────────────────────────

    /**
     * 알림 메시지를 화면 하단 알림 영역에 추가한다.
     * 관수 필요 알림 시 해당 슬롯을 빨간색으로 강조한다.
     *
     * @param message 표시할 알림 메시지
     */
    public void showNotification(String message) {
        notificationArea.append(message + "\n");
        notificationArea.setCaretPosition(notificationArea.getDocument().getLength());

        // 어느 화분 알림인지 파악하여 슬롯 색상 변경
        for (Map.Entry<Integer, PlantPot> entry : hub.getPlantPots().entrySet()) {
            int pin = entry.getKey();
            PlantPot pot = entry.getValue();
            if (message.contains(pot.getPlant().getName()) && pot.isNotified()) {
                slotPanels[pin].setBackground(new Color(255, 220, 220));
                valueLabels[pin].setForeground(Color.RED);
                waterButtons[pin].setEnabled(true);
            }
        }
    }

    /**
     * 특정 핀의 화분 슬롯에 센서 ADC 값을 갱신한다.
     * Hub의 checkPot()에서 EDT를 통해 호출된다.
     *
     * @param pin   갱신할 핀 번호
     * @param value 최신 ADC 측정값 (-1이면 이력 없음)
     */
    public void refreshPotPanel(int pin, int value) {
        if (value < 0) return;
        valueLabels[pin].setText("수분: " + value + "%");
    }
}