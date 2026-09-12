package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PokerLabActivity extends AppCompatActivity {
    private EditText heroInput;
    private EditText boardInput;
    private EditText opponentsInput;
    private TextView resultView;
    private TextView visionView;
    private Button calculateButton;

    private final PokerVisionAnalyzer visionAnalyzer = new PokerVisionAnalyzer();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> screenshotPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::analyzeScreenshot);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Poker Lab");
        setContentView(buildContent());
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private ScrollView buildContent() {
        int p = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("POKER LAB · TEXAS HOLD'EM");
        title.setTextSize(26);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Учебный расчёт equity. Формат карт: AS QH, стол: JS TS 4D");
        hint.setTextSize(15);
        hint.setPadding(0, dp(8), 0, dp(20));
        root.addView(hint);

        Button screenshot = new Button(this);
        screenshot.setText("ЗАГРУЗИТЬ СКРИНШОТ СТОЛА");
        screenshot.setOnClickListener(v -> screenshotPicker.launch("image/*"));
        root.addView(screenshot, fullWidth(0));

        visionView = new TextView(this);
        visionView.setText("Vision: скриншот ещё не выбран.");
        visionView.setTextSize(15);
        visionView.setPadding(0, dp(12), 0, dp(20));
        root.addView(visionView);

        heroInput = field("Твои 2 карты, например: AS QS");
        boardInput = field("Общие карты 0–5, например: JS TS 4D");
        opponentsInput = field("Количество соперников, например: 2");
        opponentsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(heroInput);
        root.addView(boardInput);
        root.addView(opponentsInput);

        calculateButton = new Button(this);
        calculateButton.setText("РАССЧИТАТЬ 20 000 СИМУЛЯЦИЙ");
        calculateButton.setOnClickListener(v -> calculate());
        root.addView(calculateButton, fullWidth(dp(12)));

        resultView = new TextView(this);
        resultView.setText("Введите карты и нажмите расчёт.");
        resultView.setTextSize(20);
        resultView.setPadding(0, dp(24), 0, dp(24));
        root.addView(resultView);

        return scroll;
    }

    private void analyzeScreenshot(Uri uri) {
        if (uri == null) return;
        visionView.setText("Vision: анализирую скриншот…");
        worker.execute(() -> {
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                PokerVisionObservation observation = visionAnalyzer.analyze(bitmap);

                StringBuilder regions = new StringBuilder();
                int shown = Math.min(8, observation.regions().size());
                for (int i = 0; i < shown; i++) {
                    PokerVisionObservation.Region region = observation.regions().get(i);
                    regions.append(String.format(Locale.US,
                            "\n#%d x=%.2f y=%.2f w=%.2f h=%.2f",
                            i + 1,
                            region.centerX(),
                            region.centerY(),
                            region.width(),
                            region.height()));
                }

                String text = String.format(Locale.US,
                        "Vision diagnostics\nКандидатов-карт: %d\nВероятно твои карты: %d/2\nВероятно board: %d/5\nСтадия: %s\nУверенность: %.0f%%%s\n\nРанг и масть появятся после калибровки распознавания под реальные столы.",
                        observation.cardCandidates(),
                        observation.likelyHeroCards(),
                        observation.likelyBoardCards(),
                        observation.stage(),
                        observation.confidence() * 100.0,
                        regions);
                runOnUiThread(() -> visionView.setText(text));
            } catch (Exception error) {
                runOnUiThread(() -> visionView.setText("Не удалось прочитать скриншот: " + error.getMessage()));
            }
        });
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(17);
        input.setSingleLine(true);
        input.setLayoutParams(fullWidth(dp(8)));
        return input;
    }

    private void calculate() {
        final String heroText = heroInput.getText().toString();
        final String boardText = boardInput.getText().toString();
        final String opponentsText = opponentsInput.getText().toString().trim();

        calculateButton.setEnabled(false);
        calculateButton.setText("СЧИТАЮ…");
        resultView.setText("Идёт локальный расчёт 20 000 симуляций…");

        worker.execute(() -> {
            try {
                List<Card> hero = EquityCalculator.parseCards(heroText);
                List<Card> board = EquityCalculator.parseCards(boardText);
                int opponents = Integer.parseInt(opponentsText);
                EquityCalculator.Result result = new EquityCalculator().calculate(hero, board, opponents, 20_000);
                String text = String.format(Locale.US,
                        "Победа: %.1f%%\nНичья: %.1f%%\nПоражение: %.1f%%\n\nСимуляций: %,d",
                        result.win() * 100.0,
                        result.tie() * 100.0,
                        result.lose() * 100.0,
                        result.simulations());
                runOnUiThread(() -> finishCalculation(text));
            } catch (Exception error) {
                runOnUiThread(() -> finishCalculation("Ошибка ввода: " + error.getMessage()));
            }
        });
    }

    private void finishCalculation(String text) {
        if (isFinishing() || isDestroyed()) return;
        resultView.setText(text);
        calculateButton.setEnabled(true);
        calculateButton.setText("РАССЧИТАТЬ 20 000 СИМУЛЯЦИЙ");
    }

    private LinearLayout.LayoutParams fullWidth(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
