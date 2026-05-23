/**
 * @FileName    : PlantSpecies.java
 * @Description : 식물 종류별 건조 임계값과 센서 체크 주기를 정의하는 열거형
 *
 * dryThreshold     : ADC 기준값. 이 값 이상이면 흙이 건조한 것으로 판단 (0~1023)
 * checkIntervalHours : 센서 값을 읽는 주기 (시간 단위)
 */
public enum PlantSpecies {

    SUCCULENT ("다육식물", 700, 12),
    TROPICAL  ("열대식물", 500,  6),
    HERB      ("허브",    600,  6),
    CACTUS    ("선인장",  750, 12);

    private final String displayName;
    private final int dryThreshold;
    private final int checkIntervalHours;

    PlantSpecies(String displayName, int dryThreshold, int checkIntervalHours) {
        this.displayName       = displayName;
        this.dryThreshold      = dryThreshold;
        this.checkIntervalHours = checkIntervalHours;
    }

    /** @return 화면에 표시할 종 이름 */
    public String getDisplayName() { return displayName; }

    /** @return 흙이 건조하다고 판단하는 ADC 기준값 */
    public int getDryThreshold() { return dryThreshold; }

    /** @return 센서 값을 읽는 주기 (시간) */
    public int getCheckIntervalHours() { return checkIntervalHours; }
}
