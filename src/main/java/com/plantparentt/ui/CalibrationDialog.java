package com.plantparentt.ui;

import com.plantparentt.config.AppConfig;
import com.plantparentt.sensor.MoistureSensor;
import com.plantparentt.service.CalibrationService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @FileName : CalibrationDialog.java
 * @Description : 센서 보정 UI를 담당하는 모달 다이얼로그 (SRP)
 *              보정 비즈니스 로직은 CalibrationService에 위임하고,
 *              이 클래스는 진행 상황 표시·단계 전환·결과 반환만 담당
 *
 *              진행 순서:
 *              1단계(건조) - 자동 측정 → 완료 시 '다음' 버튼 활성화
 *              2단계(포화) - 자동 측정 → 완료 시 다이얼로그 자동 닫힘
 */
public class CalibrationDialog extends JDialog {

    private final MoistureSensor sensor;
    private final CalibrationService calibService = new CalibrationService();

    // 보정 결과
    private int dryValue = -1;
    private int wetValue = -1;
    private boolean cancelled = false;

    // UI 컴포넌트
    private JLabel phaseLabel;
    private JTextArea instructionArea;
    private JLabel statusLabel;
    private JLabel progressLabel;
    private JLabel valueLabel;
    private JButton nextButton;
    private JButton cancelButton;

    private SwingWorker<Integer, int[]> activeWorker;

    /**
     * @param owner  부모 프레임 (CalibrationDialog는 부모 위에 표시)
     * @param sensor 보정할 센서 (MQTT가 이미 연결된 상태)
     */
    public CalibrationDialog(Frame owner, MoistureSensor sensor) {
        super(owner, "센서 보정", true); // modal
        this.sensor = sensor;
        buildUI();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        // setVisible(true) 호출 후 모달 루프가 시작되면 즉시 1단계(건조 상태 측정) 시작
        SwingUtilities.invokeLater(this::startDryPhase);
    }

    // ── UI 구성 ──────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(16, 16, 12, 16));

        // 단계 제목
        phaseLabel = new JLabel("【보정 1단계 - 건조 기준】", JLabel.CENTER);
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        root.add(phaseLabel, BorderLayout.NORTH);

        // 중앙: 안내문 + 상태 표시
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));

        instructionArea = new JTextArea(5, 40);
        instructionArea.setEditable(false);
        instructionArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        instructionArea.setLineWrap(true);
        instructionArea.setWrapStyleWord(true);
        instructionArea.setBackground(UIManager.getColor("Panel.background"));
        instructionArea.setBorder(BorderFactory.createEtchedBorder());
        centerPanel.add(instructionArea, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        statusLabel = new JLabel(" ", JLabel.CENTER);
        progressLabel = new JLabel("0 / " + AppConfig.CALIB_MAX_CHECKS + "회 검사", JLabel.CENTER);
        valueLabel = new JLabel("현재 ADC 값: 측정 대기 중", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        progressLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        valueLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoPanel.add(statusLabel);
        infoPanel.add(progressLabel);
        infoPanel.add(valueLabel);
        centerPanel.add(infoPanel, BorderLayout.SOUTH);

        root.add(centerPanel, BorderLayout.CENTER);

        // 하단 버튼
        cancelButton = new JButton("취소");
        nextButton = new JButton("다음 ▶");
        nextButton.setEnabled(false);
        cancelButton.addActionListener(e -> onCancel());
        nextButton.addActionListener(e -> onNextButton());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.add(cancelButton);
        btnPanel.add(nextButton);
        root.add(btnPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(500, 360));
        setContentPane(root);

        // ② X 버튼(창 닫기) 클릭 시 SwingWorker를 명시적으로 취소하고 종료
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                onCancel();
            }
        });
    }

    // ── 단계 실행 ────────────────────────────────────────────────

    /** 건조(1단계) 측정을 시작 */
    private void startDryPhase() {
        phaseLabel.setText("【보정 1단계 - 건조 기준】");
        instructionArea.setText(
                "① 마른 상태의 센서를 공기 중(흙 밖)에 두세요. (물기를 제거해 주세요)\n" +
                        "② 값이 안정되면 자동으로 측정을 완료합니다. (최소 20초 ~ 최대 1분)\n" +
                        "③ 측정 완료 후 '다음 ▶' 버튼을 눌러 포화 기준 측정으로 넘어가세요.");
        updateStatus("건조 기준 측정 중...", Color.DARK_GRAY);
        progressLabel.setText("0 / " + AppConfig.CALIB_MAX_CHECKS + "회 검사");
        valueLabel.setText("현재 ADC 값: 측정 대기 중");
        nextButton.setEnabled(false);
        nextButton.setText("다음 ▶");

        activeWorker = new SwingWorker<Integer, int[]>() {
            @Override
            protected Integer doInBackground() throws InterruptedException {
                // CalibrationService에 비즈니스 로직 위임 (DIP)
                return calibService.measureStableValue(sensor,
                        (checkNum, value) -> publish(new int[] { checkNum, value }));
            }

            // 측정 진행 상황이 publish()로 전달될 때마다 UI를 업데이트하도록 process()를 오버라이드했습니다. 
            @Override
            protected void process(List<int[]> chunks) {
                int[] latest = chunks.get(chunks.size() - 1); //chunks은 publish()로 전달된 값들의 리스트입니다. 가장 최근에 publish된 값을 가져와서 UI에 반영합니다
                int checkNum = latest[0];
                int adcValue = latest[1];
                progressLabel.setText(checkNum + " / " + AppConfig.CALIB_MAX_CHECKS + "회 검사");
                valueLabel.setText("현재 ADC 값: " +
                        (adcValue < 0 ? "수신 대기 중" : String.valueOf(adcValue)));
            }

            @Override
            protected void done() {
                if (isCancelled() || cancelled)
                    return;
                try {
                    int result = get();
                    if (result >= 0) {
                        dryValue = result;
                        updateStatus("✅ 건조 기준 측정 완료! (ADC: " + dryValue + ")", new Color(0, 128, 0));
                        nextButton.setEnabled(true); // 사용자가 명시적으로 다음 단계로 진행
                    } else {
                        showTimeoutError();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    if (!cancelled)
                        showTimeoutError();
                }
            }
        };
        activeWorker.execute();
    }

    /** 포화(2단계) 측정을 시작. 안정화되면 다이얼로그를 자동으로 닫도록 하였습니다. */
    private void startWetPhase() {
        phaseLabel.setText("【보정 2단계 - 포화 기준】");
        instructionArea.setText(
                "① 센서를 물에 완전히 담그세요.\n" +
                        "② 값이 안정되면 자동으로 측정을 완료합니다. (최소 20초 ~ 최대 1분)\n" +
                        "③ 측정 완료 후 자동으로 다음 단계로 넘어갑니다.\n" +
                        "   (건조 기준값: " + dryValue + ")");
        updateStatus("포화 기준 측정 중...", Color.DARK_GRAY);
        progressLabel.setText("0 / " + AppConfig.CALIB_MAX_CHECKS + "회 검사");
        valueLabel.setText("현재 ADC 값: 측정 대기 중");
        nextButton.setEnabled(false);

        // Swingwork를 새로 만들어 2단계 측정 시작. Swingwork는 백그라운드에서 센서 측정과 안정 판단을 수행하고, 진행 상황을 publish()로 UI에 업데이트하며, 완료 시 done()에서 결과 처리하도록 했습니다.
        activeWorker = new SwingWorker<Integer, int[]>() {
            @Override
            protected Integer doInBackground() throws InterruptedException { // throws는 CalibrationService의 measureStableValue가 InterruptedException을 던질 떄 필요합니다
                return calibService.measureStableValue(sensor,
                        (checkNum, value) -> publish(new int[] { checkNum, value }));
            }

          
            @Override
            protected void process(List<int[]> chunks) {
                int[] latest = chunks.get(chunks.size() - 1); 
                int checkNum = latest[0];
                int adcValue = latest[1];
                progressLabel.setText(checkNum + " / " + AppConfig.CALIB_MAX_CHECKS + "회 검사");
                valueLabel.setText("현재 ADC 값: " +
                        (adcValue < 0 ? "수신 대기 중" : String.valueOf(adcValue)));
            }

            @Override
            protected void done() {
                if (isCancelled() || cancelled)
                    return;
                try {
                    int result = get();
                    if (result >= 0) {
                        wetValue = result;

                        // ③ 보정값 유효성 검사 (CalibrationService에 위임 — SRP를 지키기 위함)
                        if (!calibService.isCalibrationValid(dryValue, wetValue)) {
                            updateStatus("⚠ 보정 값이 올바르지 않습니다.", Color.RED);
                            JOptionPane.showMessageDialog(CalibrationDialog.this,
                                    "보정 값이 올바르지 않습니다.\n" +
                                            "물 속 값이 공기 중 값보다 충분히 높아야 합니다.\n\n" +
                                            "공기 중(건조): " + dryValue + "  /  물 속(포화): " + wetValue,
                                    "보정 오류", JOptionPane.ERROR_MESSAGE);
                            cancelled = true;
                            dispose();
                            return;
                        }

                        updateStatus("✅ 포화 기준 측정 완료! (ADC: " + wetValue + ")", new Color(0, 128, 0));
                        // 1.5초 뒤 자동 닫힘 (사용자가 결과를 확인할 시간)
                        javax.swing.Timer closeTimer = new javax.swing.Timer(1500, ev -> dispose());
                        closeTimer.setRepeats(false);
                        closeTimer.start();
                    } else {
                        showTimeoutError();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    if (!cancelled)
                        showTimeoutError();
                }
            }
        };
        activeWorker.execute();
    }

    // ── 버튼 핸들러 ─────────────────────────────────────────────

    /** 건조 단계 완료 후 사용자가 다음 버튼을 클릭하면 포화 단계로 진행 */
    private void onNextButton() {
        nextButton.setEnabled(false);
        startWetPhase();
    }

    /** '취소' 버튼 또는 창 닫기 시 호출된다. */
    private void onCancel() {
        cancelled = true;
        if (activeWorker != null)
            activeWorker.cancel(true);
        dispose();
    }

    /** 1분 내에 안정값을 찾지 못한 경우 오류 메시지를 표시하고 다이얼로그를 닫기 */
    private void showTimeoutError() {
        updateStatus("⚠ 측정 실패: 값이 1분 내에 안정되지 않았습니다.", Color.RED);
        JOptionPane.showMessageDialog(this,
                "센서 값이 1분(60초) 내에 안정되지 않았습니다.\n\n" +
                        "• 아두이노가 올바르게 연결되어 있는지 확인하세요.\n" +
                        "• 센서가 움직이지 않도록 고정한 뒤 다시 시도하세요.",
                "보정 오류", JOptionPane.ERROR_MESSAGE);
        cancelled = true;
        dispose();
    }

    // ── 유틸리티 ────────────────────────────────────────────────

    private void updateStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    // ── 결과 반환 ────────────────────────────────────────────────

    /**
     * 보정 결과를 반환하는 메서드
     *
     * @return 보정 성공 시 {dryValue, wetValue}, 취소·실패 시 null
     */
    public int[] getResult() {
        if (!cancelled && dryValue >= 0 && wetValue >= 0) {
            return new int[] { dryValue, wetValue };
        }
        return null;
    }
}
