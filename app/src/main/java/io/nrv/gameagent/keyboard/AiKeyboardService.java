package io.nrv.gameagent.keyboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.nrv.gameagent.poker.PokerLabActivity;

/**
 * NRV AI Keyboard is the primary on-screen companion UI.
 * Screen capture is always requested through Android's MediaProjection consent.
 */
public final class AiKeyboardService extends InputMethodService {
    private TextView insightView;
    private TextView modeView;

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6), dp(5), dp(6), dp(5));

        LinearLayout modeRow = row();
        modeRow.addView(tool("AI", v -> setMode(CompanionModeStore.Mode.GENERAL)));
        modeRow.addView(tool("POKER", v -> setMode(CompanionModeStore.Mode.POKER)));
        modeRow.addView(tool("MOBA", v -> setMode(CompanionModeStore.Mode.MOBA)));
        modeRow.addView(tool("ЭКРАН", v -> requestScreenCapture()));
        root.addView(modeRow);

        modeView = new TextView(this);
        modeView.setTextSize(12);
        modeView.setTypeface(Typeface.DEFAULT_BOLD);
        modeView.setPadding(dp(6), dp(4), dp(6), 0);
        root.addView(modeView);

        insightView = new TextView(this);
        insightView.setTextSize(13);
        insightView.setTypeface(Typeface.DEFAULT_BOLD);
        insightView.setPadding(dp(6), dp(4), dp(6), dp(6));
        insightView.setMaxLines(4);
        root.addView(insightView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout tools = row();
        tools.addView(tool("ОБНОВИТЬ", v -> refreshInsight()));
        tools.addView(tool("POKER LAB", v -> openPokerLab()));
        tools.addView(tool("⌨", v -> showInputMethodPicker()));
        root.addView(tools);

        root.addView(letterRow("QWERTYUIOP"));
        root.addView(letterRow("ASDFGHJKL"));
        root.addView(letterRow("ZXCVBNM"));

        LinearLayout bottom = row();
        bottom.addView(tool("⌫", v -> deleteOne()));
        bottom.addView(tool("ПРОБЕЛ", v -> commit(" ")));
        bottom.addView(tool("↵", v -> sendEnter()));
        root.addView(bottom);

        refreshInsight();
        return root;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshInsight();
    }

    private void setMode(CompanionModeStore.Mode mode) {
        CompanionModeStore.set(this, mode);
        refreshInsight();
    }

    private void requestScreenCapture() {
        Intent intent = new Intent(this, KeyboardCaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    private void showInputMethodPicker() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showInputMethodPicker();
    }

    private LinearLayout letterRow(String letters) {
        LinearLayout row = row();
        for (int i = 0; i < letters.length(); i++) {
            String letter = String.valueOf(letters.charAt(i));
            row.addView(key(letter, v -> commit(letter.toLowerCase())));
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
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
        p.setMargins(dp(1), dp(1), dp(1), dp(1));
        button.setLayoutParams(p);
        return button;
    }

    private Button tool(String text, View.OnClickListener listener) {
        Button button = key(text, listener);
        button.setTextSize(10);
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
        CompanionModeStore.Mode mode = CompanionModeStore.get(this);
        if (modeView != null) modeView.setText("NRV AI · " + CompanionModeStore.title(mode));

        ScreenInsightStore.Snapshot snapshot = ScreenInsightStore.read();
        if (insightView != null) {
            insightView.setText(snapshot.fresh(20_000)
                    ? snapshot.text()
                    : "Нет свежего анализа. Выбери режим и нажми «ЭКРАН».");
        }
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
