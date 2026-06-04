package com.plantparentt.ui;

import com.plantparentt.config.AppConfig;
import com.plantparentt.sensor.MoistureSensor;
import com.plantparentt.service.Hub;
import com.plantparentt.service.PlantMonitorView;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * @FileName : ControlUI.java
 * @Description : 식집사 메인 GUI 클래스 (Java Swing)
 *              화분 슬롯 6칸 표시, 식물 생성/삭제/물주기 기능, 알림 패널을 제공하는 JFrame입니다
 */
public class ControlUI extends JFrame implements PlantMonitorView {

    private static final int MAX_POTS = Hub.MAX_POTS;

    private final Hub hub;

    // UI 컴포넌트 목록은 배열로 관리하여 인덱스(핀 번호)로 쉽게 접근할 수 있도록 했습니다
    private final JPanel[] slotPanels = new JPanel[MAX_POTS];
    private final JPanel[] btnPanels = new JPanel[MAX_POTS]; // 버튼 컨테이너 (동적 추가/제거용)
    private final JLabel[] nameLabels = new JLabel[MAX_POTS];
    private final JLabel[] valueLabels = new JLabel[MAX_POTS];
    private final JButton[] actionButtons = new JButton[MAX_POTS]; // 식물 생성 or 삭제
    private final JButton[] checkButtons = new JButton[MAX_POTS]; // 수분량 체크 (식물 등록 시 생성)
    private final JTextArea notificationArea;

    // ── ControlUI 생성자 ─────────────────────────────────────────────

    // Hub 인스턴스를 주입받아 GUI를 초기화
    // ControlUI는 PlantMonitorView를 구현하여 Hub에 view로 등록되고
    // Hub는 PlantMonitorView 인터페이스를 통해 알림·갱신을 전달 -> DIP 구현하려했습니다
    public ControlUI(Hub hub) {
        this.hub = hub;
        hub.setView(this);

        // ── 프레임 설정 부분 ───────────────────────────────────────────
        setTitle("그린벨 🌿");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── 상단: 제목 타이틀 ─────────────────────────────────────
        JLabel title = new JLabel("🌱 그린벨", JLabel.CENTER);
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
        nameLabels[pin] = new JLabel("비어있음", JLabel.CENTER);
        valueLabels[pin] = new JLabel("수분: --", JLabel.CENTER);
        nameLabels[pin].setFont(new Font("SansSerif", Font.BOLD, 13));
        valueLabels[pin].setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setOpaque(false);
        infoPanel.add(nameLabels[pin]);
        infoPanel.add(valueLabels[pin]);

        // 버튼 영역 - 초기에는 "식물 생성" 버튼만 존재
        // "수분량 체크" 버튼은 식물 등록 후 동적으로 추가됨
        actionButtons[pin] = new JButton("식물 생성");

        final int p = pin;
        actionButtons[pin].addActionListener(e -> onActionButton(p));

        JPanel btnPanel = new JPanel(new GridLayout(1, 1));
        btnPanel.setOpaque(false);
        btnPanel.add(actionButtons[pin]);

        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        btnPanels[pin] = btnPanel;
        slotPanels[pin] = panel;
        return panel;
    }

    // ── 버튼 핸들러 ────────────────────────────────────────────

    /** "수분량 체크" 버튼 클릭 처리 */
    private void onCheckButton(int pin) {
        hub.checkPotNow(pin);
    }

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

    /** 식물 생성 다이얼로그 표시 (2점 자동 보정 포함) */
    private void showCreateDialog(int pin) {
        // ── 센서 객체 생성 (Hub → SensorFactory에 위임) ──────────
        MoistureSensor sensor = hub.createSensor(pin);

        // ── MQTT 연결 실패 시 경고 팝업 ──────────────────────────
        // App Class 시작 시 브로커 연결을 확인했더라도, 식물 생성 시점에 브로커가 끊길 수 있으므로 재확인
        if (!sensor.isConnected()) {
            JOptionPane.showMessageDialog(
                    this,
                    "센서가 MQTT 브로커에 연결되지 않았습니다.\n"
                            + "브로커 IP : " + AppConfig.BROKER_IP + "\n"
                            + "포트      : " + AppConfig.BROKER_PORT + "\n\n"
                            + "Mosquitto가 실행 중인지 확인해 주세요.",
                    "센서 연결 실패",
                    JOptionPane.ERROR_MESSAGE);
            sensor.disconnect();
            return;
        }

        // ── 자동 보정 다이얼로그 실행 (CalibrationDialog에 UI·CalibrationService에 로직 위임) ──
        CalibrationDialog calDialog = new CalibrationDialog(this, sensor);
        calDialog.setVisible(true); // modal: 보정 완료 또는 취소까지 대기
   

        int[] calibResult = calDialog.getResult();
        if (calibResult == null) {
            // 취소 또는 타임아웃 → 센서 연결 해제 후 종료
            sensor.disconnect();
            return;
        }

        // 보정값 유효성 검사는 CalibrationDialog 내부(CalibrationService)에서 완료되도록 했으므로
        // null이 아닌 결과는 이미 유효성이 검증된 값입니다. 따라서 바로 센서에 적용하면 됩니다
        sensor.setCalibration(calibResult[0], calibResult[1]);

        // ── 식물 이름 입력 ───────────────────────────────────────
        String name = JOptionPane.showInputDialog(this,
                "식물 이름을 입력하세요. (생략 시 \"내 식물\"로 대체)",
                "식물 생성", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            sensor.disconnect();
            return;
        } // 취소

        // ── 센서 삽입 안내 ───────────────────────────────────────
        int ready = JOptionPane.showConfirmDialog(this,
                "센서를 화분에 꽂은 후 확인을 누르세요.",
                "센서 삽입", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        // 센서가 꽂혀있지 않거나 사용자가 취소하면 등록 취소하고 센서 연결 해제
        if ((ready != JOptionPane.OK_OPTION) || !sensor.isInserted()) {
            sensor.disconnect();
            return;
        }

        // ── 등록 ────────────────────────────────────────────────
        if (hub.addPlantPot(pin, name, sensor)) {
            setSlotOccupied(pin, name.isBlank() ? "내 식물" : name);
        } else {
            sensor.disconnect();
        }
    }

    // ── 슬롯 상태 변경 ──────────────────────────────────────────

    private void setSlotEmpty(int pin) {
        nameLabels[pin].setText("비어있음");
        valueLabels[pin].setText("수분: --");
        valueLabels[pin].setForeground(Color.DARK_GRAY);
        actionButtons[pin].setText("식물 생성");
        slotPanels[pin].setBackground(new Color(245, 245, 240));

        // "수분량 체크" 버튼 제거
        if (checkButtons[pin] != null) {
            btnPanels[pin].remove(checkButtons[pin]);
            checkButtons[pin] = null;
            btnPanels[pin].setLayout(new GridLayout(1, 1));
            btnPanels[pin].revalidate();
            btnPanels[pin].repaint();
        }
    }

    private void setSlotOccupied(int pin, String plantName) {
        nameLabels[pin].setText(plantName);
        valueLabels[pin].setText("수분: 측정 중...");
        actionButtons[pin].setText("삭제");

        // "수분량 체크" 버튼 동적 생성 및 추가
        checkButtons[pin] = new JButton("수분량 체크 💧");
        checkButtons[pin].addActionListener(e -> onCheckButton(pin));
        btnPanels[pin].setLayout(new GridLayout(1, 2, 4, 0));
        btnPanels[pin].add(checkButtons[pin]);
        btnPanels[pin].revalidate();
        btnPanels[pin].repaint();
    }

    // ── PlantMonitorView 구현 ────────────────────────────────────
    // Hub는 스케줄러 스레드에서 이 메서드들을 직접 호출!
    // Swing 컴포넌트 조작은 반드시 EDT에서 실행되어야 하므로
    // EDT dispatch 책임은 구현체인 ControlUI가 직접 담당하도록 했습니다

    /**
     * 알림 메시지를 화면 하단 알림 영역에 추가하는 메서드
     *
     * @param message 표시할 알림 메시지
     */
    @Override
    public void showNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            notificationArea.append(message + "\n");
            notificationArea.setCaretPosition(notificationArea.getDocument().getLength());
        });
    }

    /**
     * 건조 알림 발송 시 해당 슬롯을 빨간색으로 강조하는 메서드
     *
     * @param pin 강조할 핀 번호
     */
    @Override
    public void markSlotDry(int pin) {
        SwingUtilities.invokeLater(() -> {
            slotPanels[pin].setBackground(new Color(255, 220, 220));
            valueLabels[pin].setForeground(Color.RED);
        });
    }

    /**
     * 특정 핀의 화분 슬롯에 수분 퍼센트를 갱신하는 메서드
     *
     * @param pin   갱신할 핀 번호
     * @param value 최신 수분 % (-1이면 이력 없음)
     */
    @Override
    public void refreshPotPanel(int pin, int value) {
        if (value < 0)
            return;
        SwingUtilities.invokeLater(() -> valueLabels[pin].setText("수분: " + value + "%"));
    }

    /**
     * 관수 감지 후 슬롯의 빨간색 강조를 원래 색상으로 되돌리는 메서드
     *
     * @param pin 초기화할 핀 번호
     */
    @Override
    public void resetPotPanel(int pin) {
        SwingUtilities.invokeLater(() -> {
            slotPanels[pin].setBackground(new Color(245, 245, 240));
            valueLabels[pin].setForeground(Color.DARK_GRAY);
        });
    }
}
