package io.nrv.gameagent.keyboard;

import android.content.Intent;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.nrv.gameagent.MainActivity;
import io.nrv.gameagent.poker.PokerLabActivity;

/**
 * Minimal on-device AI companion keyboard.
 * It never captures the screen by itself: screen data only comes from the
 * separately approved MediaProjection flow in the companion app.
 */
public final class AiKeyboardService extends InputMethodService {
    private TextView insightView;

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout tools = row();
        tools.addView(tool("AI", v -> refreshInsight()));
        tools.addView(tool("АНАЛИЗ ЭКРАНА", v -> openCompanion()));
        tools.addView(tool("POKER LAB", v -> openPokerLab()));
        root.addView(tools);

        insightView = new TextView(this);
        insightView.setTextSize(13);
        insightView.setTypeface(Typeface.DEFAULT_BOLD);
        insightView.setPadding(dp(6), dp(6), dp(6), dp(6));
        insightView.setMaxLines(3);
        root.addView(insightView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        refreshInsight();

        root.addView(letterRow("QWERTYUIOP"));
        root.addView(letterRow("ASDFGHJKL"));
        root.addView(letterRow("ZXCVBNM"));

        LinearLayout bottom = row();
        bottom.addView(tool("⌫", v -> deleteOne()));
        bottom.addView(tool("ПРОБЕЛ", v -> commit(" ")));
        bottom.addView(tool("↵", v -> sendEnter()));
        root.addView(bottom);

        return root;
    }

    private LinearLayout letterRow(String letters) {
        LinearLayout row = row();
        for (int i = 0; i < letters.length(); i++) {
            String letter = String.valueOf(letters.charAt(i));
            Button key = key(letter, v -> commit(letter.toLowerCase()));
            row.addView(key);
        }
        return row;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private Button key(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(1), dp(1), dp(1), dp(1));
        button.setLayoutParams(p);
        return button;
    }

    private Button tool(String text, View.OnClickListener listener) {
        Button button = key(text, listener);
        button.setTextSize(11);
        return button;
    }

    private void commit(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }

    private void deleteOne() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.deleteSurroundingText(1, 0);
    }

    private void sendEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText("\n", 1);
    }

    private void refreshInsight() {
        ScreenInsightStore.Snapshot snapshot = ScreenInsightStore.read();
        if (insightView != null) {
            insightView.setText(snapshot.fresh(15_000)
                    ? snapshot.text()
                    : "Нет свежего анализа. Запусти разрешённый захват экрана в NRV Game Agent.");
        }
    }

    private void openCompanion() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openPokerLab() {
        Intent intent = new Intent(this, PokerLabActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public static Intent inputMethodSettingsIntent() {
        return new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
