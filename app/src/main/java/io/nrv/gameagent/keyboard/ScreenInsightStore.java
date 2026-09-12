package io.nrv.gameagent.keyboard;

/**
 * Small in-process bridge between the explicitly approved screen capture service
 * and the optional AI keyboard. It stores text diagnostics only, never raw frames.
 */
public final class ScreenInsightStore {
    private static volatile String latest = "Захват экрана не запущен";
    private static volatile long updatedAtMs = 0L;

    private ScreenInsightStore() {}

    public static void publish(String text) {
        latest = text == null || text.isBlank() ? "Нет данных анализа" : text;
        updatedAtMs = System.currentTimeMillis();
    }

    public static Snapshot read() {
        return new Snapshot(latest, updatedAtMs);
    }

    public record Snapshot(String text, long updatedAtMs) {
        public boolean fresh(long maxAgeMs) {
            return updatedAtMs > 0 && System.currentTimeMillis() - updatedAtMs <= maxAgeMs;
        }
    }
}
